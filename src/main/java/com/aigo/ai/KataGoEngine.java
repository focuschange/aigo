package com.aigo.ai;

import com.aigo.config.EngineProperties;
import com.aigo.model.MoveRecord;
import com.aigo.model.Stone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KataGo GTP (Go Text Protocol) engine integration.
 * Manages a single KataGo subprocess and routes GTP commands through it.
 * All public methods are thread-safe.
 */
@Component
public class KataGoEngine {

    private static final Logger log = LoggerFactory.getLogger(KataGoEngine.class);
    private static final String GTP_COLUMNS = "ABCDEFGHJKLMNOPQRST"; // no 'I'

    @Value("${katago.executable}")
    private String executable;

    @Value("${katago.model}")
    private String model;

    @Value("${katago.config}")
    private String config;

    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private BufferedReader errReader;
    /**
     * KataGo 프로세스 접근 직렬화용 fair 락.
     * fair=true 로 설정하여 힌트 스팸 상황에서도 다른 요청이 FIFO 로 순서를 얻도록 한다.
     */
    private final ReentrantLock lock = new ReentrantLock(true);
    private final EngineProperties engineProps;
    private boolean available = false;

    public KataGoEngine(EngineProperties engineProps) {
        this.engineProps = engineProps;
    }

    @PostConstruct
    public void start() {
        try {
            log.info("Starting KataGo: {} gtp -model {} -config {}", executable, model, config);
            ProcessBuilder pb = new ProcessBuilder(executable, "gtp", "-model", model, "-config", config);
            pb.redirectErrorStream(false);
            process = pb.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Drain stderr in background
            Thread stderrThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        log.debug("[KataGo] {}", line);
                    }
                } catch (IOException ignored) {}
            }, "katago-stderr");
            stderrThread.setDaemon(true);
            stderrThread.start();

            // Wait for KataGo to be ready (first prompt)
            waitForReady();
            available = true;
            log.info("KataGo ready.");
        } catch (Exception e) {
            log.error("Failed to start KataGo: {}", e.getMessage());
            log.error("Please check katago.executable, katago.model, katago.config in application.properties");
            available = false;
        }
    }

    private void waitForReady() throws IOException, InterruptedException {
        // KataGo outputs its startup messages to stderr.
        // We send a 'name' command and wait for the response.
        Thread.sleep(1000); // Give KataGo time to initialize
        String response = sendRawCommand("name");
        log.info("KataGo name: {}", response);
    }

    /** AI 착수 결과 (좌표 + 승률). */
    public record MoveResult(int[] move, double blackWinRate) {}

    /** 힌트용 추천수 (좌표 + 승률 + 순위). */
    public record HintMove(int row, int col, double winRate, int order) {}

    /**
     * Generate AI move for the given game state.
     *
     * @param moveHistory full move history of the game
     * @param boardSize   board size (9, 13, 19)
     * @param aiColor     AI's stone color
     * @param difficulty  EASY / MEDIUM / HARD
     * @return MoveResult with [row, col] (or null for pass) and blackWinRate (-1 if unknown)
     */
    public MoveResult generateMove(List<MoveRecord> moveHistory, int boardSize, Stone aiColor, String difficulty) {
        if (!available) {
            log.error("KataGo is not available");
            return new MoveResult(null, -1);
        }
        acquireLockOrThrow("generateMove");
        try {
            // Set difficulty via visit limit
            int visits = switch (difficulty.toUpperCase()) {
                case "EASY"   -> 100;
                case "MEDIUM" -> 500;
                default       -> 0;  // 0 = no limit (HARD: time-based in config)
            };

            // Reconstruct board state
            sendRawCommand("clear_board");
            sendRawCommand("boardsize " + boardSize);
            sendRawCommand("komi 6.5");
            // 한국식 규칙 적용 (집계산 방식: territory + captures)
            sendRawCommand("kata-set-rules korean");

            if (visits > 0) {
                // KataGo extension: limit visits per move
                sendRawCommand("kata-set-param maxVisits " + visits);
            }

            // Replay all moves
            for (MoveRecord rec : moveHistory) {
                String colorStr = rec.color.toGTP();
                String coord = rec.pass ? "pass" : toGTPCoord(rec.row, rec.col, boardSize);
                sendRawCommand("play " + colorStr + " " + coord);
            }

            // Request move
            String response = sendRawCommand("genmove " + aiColor.toGTP());
            log.info("KataGo genmove response: {}", response);

            int[] move = parseGTPCoord(response, boardSize);

            // Query win rate after move via neural network evaluation
            double blackWinRate = queryBlackWinRate();

            return new MoveResult(move, blackWinRate);

        } catch (IOException e) {
            log.error("KataGo communication error: {}", e.getMessage());
            return new MoveResult(null, -1);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 현재 보드 상태에서 흑의 승률을 조회한다.
     * 착수 히스토리가 이미 KataGo에 재생된 상태에서 호출해야 한다.
     * lock은 호출자가 이미 획득한 상태여야 한다.
     */
    public double queryBlackWinRate(List<MoveRecord> moveHistory, int boardSize) {
        if (!available) return -1;
        acquireLockOrThrow("queryBlackWinRate");
        try {
            sendRawCommand("clear_board");
            sendRawCommand("boardsize " + boardSize);
            sendRawCommand("komi 6.5");
            sendRawCommand("kata-set-rules korean");

            for (MoveRecord rec : moveHistory) {
                String colorStr = rec.color.toGTP();
                String coord = rec.pass ? "pass" : toGTPCoord(rec.row, rec.col, boardSize);
                sendRawCommand("play " + colorStr + " " + coord);
            }

            return queryBlackWinRate();
        } catch (IOException e) {
            log.error("KataGo win rate query error: {}", e.getMessage());
            return -1;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 현재 KataGo 내부 보드 상태에서 흑 승률을 조회 (lock 획득된 상태에서 호출).
     */
    private double queryBlackWinRate() {
        try {
            String nnResponse = sendRawCommand("kata-raw-nn 0");
            log.debug("kata-raw-nn response: {}", nnResponse);
            return parseBlackWinRate(nnResponse);
        } catch (IOException e) {
            log.warn("Failed to query win rate: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * kata-raw-nn 응답에서 흑 승률을 파싱한다.
     * 응답 형식: "whiteWin 0.487 whiteLoss 0.513 ..."
     * blackWinRate ≈ whiteLoss
     */
    private double parseBlackWinRate(String response) {
        try {
            String[] tokens = response.split("\\s+");
            for (int i = 0; i < tokens.length - 1; i++) {
                if ("whiteLoss".equals(tokens[i])) {
                    return Double.parseDouble(tokens[i + 1]);
                }
            }
            // fallback: 1 - whiteWin
            for (int i = 0; i < tokens.length - 1; i++) {
                if ("whiteWin".equals(tokens[i])) {
                    return 1.0 - Double.parseDouble(tokens[i + 1]);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse win rate from: {}", response);
        }
        return -1;
    }

    /**
     * 현재 포지션에서 최대 maxMoves개의 추천수를 분석한다.
     * kata-analyze 스트리밍 명령을 사용한다.
     *
     * kata-analyze 출력 형식 (실제 확인):
     *   1) 초기 GTP 응답: "=\n\n"
     *   2) 분석 갱신: 수마다 별도 줄 ("info move D4 visits 231 winrate 0.816 ... order 0")
     *   3) 갱신 배치 사이에 빈 줄 구분
     */
    public List<HintMove> getHints(List<MoveRecord> moveHistory, int boardSize, Stone colorToPlay, int maxMoves) {
        if (!available) return List.of();
        acquireLockOrThrow("getHints");
        try {
            // 보드 셋업
            sendRawCommand("clear_board");
            sendRawCommand("boardsize " + boardSize);
            sendRawCommand("komi 6.5");
            sendRawCommand("kata-set-rules korean");

            for (MoveRecord rec : moveHistory) {
                String colorStr = rec.color.toGTP();
                String coord = rec.pass ? "pass" : toGTPCoord(rec.row, rec.col, boardSize);
                sendRawCommand("play " + colorStr + " " + coord);
            }

            // kata-analyze 시작 (interval: 100 centisec = 1초)
            writer.write("kata-analyze " + colorToPlay.toGTP() + " 100 maxmoves " + maxMoves + "\n");
            writer.flush();

            // 초기 "=\n\n" GTP 응답 소비 (reader.ready() 논블로킹)
            consumeInitialAck();

            // 논블로킹으로 약 2.5초간 분석 결과 수집
            // 수마다 별도 줄이므로 최신 배치의 전체 줄을 수집한다
            List<String> currentBatch = new ArrayList<>();
            List<String> lastCompleteBatch = new ArrayList<>();
            long deadline = System.currentTimeMillis() + 2500;
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    if (line.startsWith("info ")) {
                        currentBatch.add(line);
                    } else if (line.isEmpty() && !currentBatch.isEmpty()) {
                        // 빈 줄 = 배치 종료 → 현재 배치를 최신 완료 배치로 저장
                        lastCompleteBatch = new ArrayList<>(currentBatch);
                        currentBatch.clear();
                    }
                } else {
                    Thread.sleep(50);
                }
            }

            // 분석 중단 후 GTP 상태 동기화
            syncAfterAnalysis();

            // 마지막 미완료 배치가 있으면 그것을 사용, 없으면 완료 배치 사용
            List<String> bestBatch = !currentBatch.isEmpty() ? currentBatch : lastCompleteBatch;
            if (bestBatch.isEmpty()) {
                log.info("No analysis data received from kata-analyze");
                return List.of();
            }

            log.info("Parsing {} analysis lines from kata-analyze", bestBatch.size());
            return parseAnalysisLines(bestBatch, boardSize, colorToPlay);

        } catch (Exception e) {
            log.error("Hint analysis error: {}", e.getMessage());
            try { syncAfterAnalysis(); } catch (Exception ignored) {}
            return List.of();
        } finally {
            lock.unlock();
        }
    }

    /**
     * kata-analyze 초기 GTP 응답 ("=\n\n") 을 소비한다.
     */
    private void consumeInitialAck() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (reader.ready()) {
                    String line = reader.readLine();
                    if (line == null) break;
                    // "=" 또는 "= " 응답 → 무시하고 계속
                    // 빈 줄 → 초기 응답 완료
                    if (line.isEmpty()) break;
                    // 혹시 info 줄이 바로 오면 → 초기 응답 없이 분석 시작 (push back 불가하므로 로그만)
                    if (line.startsWith("info ")) {
                        log.debug("Got analysis line during ack phase, initial ack may be absent");
                        break;
                    }
                } else {
                    Thread.sleep(50);
                }
            } catch (IOException e) {
                break;
            }
        }
    }

    /**
     * kata-analyze 등 스트리밍 명령 중단 후 GTP 상태를 동기화한다.
     * "name" 명령을 보내 분석을 중단시키고, 응답("= KataGo\n\n")까지 완전히 소비한다.
     */
    private void syncAfterAnalysis() throws IOException, InterruptedException {
        writer.write("name\n");
        writer.flush();

        long deadline = System.currentTimeMillis() + 3000;
        boolean seenGtpResponse = false;
        while (System.currentTimeMillis() < deadline) {
            if (reader.ready()) {
                String line = reader.readLine();
                if (line == null) break;
                if (!seenGtpResponse) {
                    if (line.startsWith("= ") || line.equals("=") || line.startsWith("? ")) {
                        seenGtpResponse = true;
                    }
                    // 잔여 info 줄 등은 무시
                } else {
                    if (line.isEmpty()) break; // GTP 응답 뒤 빈 줄 → 동기화 완료
                }
            } else {
                if (seenGtpResponse) break;
                Thread.sleep(50);
            }
        }
        log.debug("GTP state synced after analysis (response seen: {})", seenGtpResponse);
    }

    // kata-analyze 출력에서 개별 수 정보를 추출하는 패턴
    private static final Pattern MOVE_PATTERN = Pattern.compile(
        "info move (\\S+) visits (\\d+).*?winrate ([\\d.eE+-]+).*?order (\\d+)"
    );
    private static final Pattern MOVE_PATTERN_NO_ORDER = Pattern.compile(
        "info move (\\S+) visits (\\d+).*?winrate ([\\d.eE+-]+)"
    );

    /**
     * kata-analyze 출력 줄들을 파싱하여 HintMove 리스트를 반환한다.
     * 각 줄 형식: "info move D4 visits 231 ... winrate 0.816715 ... order 0 pv ..."
     * winrate는 colorToPlay 관점이므로 흑 관점으로 변환한다.
     */
    private List<HintMove> parseAnalysisLines(List<String> lines, int boardSize, Stone colorToPlay) {
        List<HintMove> hints = new ArrayList<>();
        int autoOrder = 0;
        for (String line : lines) {
            Matcher m = MOVE_PATTERN.matcher(line);
            String coordStr;
            double winRate;
            int order;
            if (m.find()) {
                coordStr = m.group(1);
                winRate = Double.parseDouble(m.group(3));
                order = Integer.parseInt(m.group(4));
            } else {
                m = MOVE_PATTERN_NO_ORDER.matcher(line);
                if (!m.find()) continue;
                coordStr = m.group(1);
                winRate = Double.parseDouble(m.group(3));
                order = autoOrder;
            }
            autoOrder++;

            int[] coord = parseGTPCoord(coordStr, boardSize);
            if (coord == null) continue;

            double blackWin = (colorToPlay == Stone.BLACK) ? winRate : 1.0 - winRate;
            hints.add(new HintMove(coord[0], coord[1], blackWin, order));
        }
        hints.sort((a, b) -> Integer.compare(a.order(), b.order()));
        log.info("Parsed {} hint moves", hints.size());
        return hints;
    }

    /**
     * KataGo 락 획득을 시도하되 {@link EngineProperties#getLockTimeoutMs()} 초과 시
     * {@link EngineBusyException} 을 던진다.
     *
     * Tomcat 요청 스레드가 무한정 블로킹되어 스레드 풀이 고갈되는 것을 막기 위한 방어선이다.
     */
    private void acquireLockOrThrow(String opName) {
        long timeoutMs = engineProps.getLockTimeoutMs();
        boolean acquired;
        try {
            acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for KataGo lock ({})", opName);
            throw new EngineBusyException(Math.max(1, timeoutMs / 1000));
        }
        if (!acquired) {
            long retrySec = Math.max(1, timeoutMs / 1000);
            log.warn("KataGo busy: {} 락 획득 타임아웃 ({}ms). retry-after={}s", opName, timeoutMs, retrySec);
            throw new EngineBusyException(retrySec);
        }
    }

    /** Send a raw GTP command and return the response (without leading "= " or "? "). */
    private String sendRawCommand(String command) throws IOException {
        writer.write(command + "\n");
        writer.flush();

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break;   // GTP responses end with blank line
            sb.append(line).append("\n");
        }
        String response = sb.toString().trim();
        // Strip leading "= " or "? "
        if (response.startsWith("= ")) return response.substring(2).trim();
        if (response.startsWith("="))  return response.substring(1).trim();
        if (response.startsWith("? ")) throw new IOException("KataGo error: " + response.substring(2));
        return response;
    }

    /** Convert internal [row, col] (0-indexed, row 0 = top) to GTP coordinate (e.g. "D16"). */
    public String toGTPCoord(int row, int col, int size) {
        return "" + GTP_COLUMNS.charAt(col) + (size - row);
    }

    /** Parse GTP coordinate string (e.g. "D16" or "pass") to [row, col]. Returns null for pass. */
    public int[] parseGTPCoord(String coord, int size) {
        if (coord == null) return null;
        String upper = coord.trim().toUpperCase();
        if (upper.equals("PASS") || upper.equals("RESIGN") || upper.isEmpty()) return null;
        try {
            int col = GTP_COLUMNS.indexOf(upper.charAt(0));
            int row = size - Integer.parseInt(upper.substring(1));
            if (col < 0 || row < 0 || row >= size || col >= size) return null;
            return new int[]{row, col};
        } catch (NumberFormatException e) {
            log.error("Failed to parse GTP coord: {}", coord);
            return null;
        }
    }

    public boolean isAvailable() { return available; }

    @PreDestroy
    public void stop() {
        if (process != null && process.isAlive()) {
            try {
                writer.write("quit\n");
                writer.flush();
            } catch (IOException ignored) {}
            process.destroy();
            log.info("KataGo stopped.");
        }
    }
}
