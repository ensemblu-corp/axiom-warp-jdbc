package com.ensemblu.axiom.jdbc.io;

import com.ensemblu.axiom.api.Axiom;
import com.ensemblu.axiom.core.validation.*;
import com.ensemblu.axiom.core.data_structure.list.PersistentList;
import com.ensemblu.axiom.core.data_structure.map.PersistentMap;
import com.ensemblu.axiom.spec.parser.CsvRowParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public interface CsvEngine {

    interface AddHeaders {
        default Result<CsvStream> basedOnHeaders(String... headers) {
            return basedOnHeaders(Axiom.Data.list(headers));
        }

        Result<CsvStream> basedOnHeaders(PersistentList<String> headers);

        Result<CsvStream> usingFileHeaders();
    }

    static AddHeaders streamFromPath(final String path) {
        return new AddHeaders() {
            @Override
            public Result<CsvStream> basedOnHeaders(PersistentList<String> headers) {
                if (headers != null && headers.isEmpty()) {
                    return Axiom.Check.failure("Contract Breach: basedOnHeaders() requires at least one header.");
                }
                return open(path, headers);
            }

            @Override
            public Result<CsvStream> usingFileHeaders() {
                return open(path, null);
            }
        };
    }

    private static Result<CsvStream> open(String inputPath, PersistentList<String> headers) {
        return If.givenObject(inputPath)//
                .isNonNull("CSV Path")//
                .will()//
                .getResult()//
                .flatMapTry(rawPath -> Axiom.Check.attempt(() -> {
                    var loader = Thread.currentThread().getContextClassLoader();
                    if (loader == null) loader = CsvEngine.class.getClassLoader();

                    final var resolvedPath = rawPath.endsWith(".csv") ? rawPath : rawPath + ".csv";

                    final var is = loader.getResourceAsStream(resolvedPath);
                    if (is == null) throw new RuntimeException("Resource [" + resolvedPath + "] missing.");

                    final var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    final PersistentList<String> finalHeaders;

                    if (headers == null) {
                        final var firstLine = reader.readLine();
                        if (firstLine == null) {
                            reader.close();
                            return CsvStream.empty();
                        }
                        finalHeaders = CsvRowParser.scanLine(firstLine);
                    } else {
                        finalHeaders = headers;
                    }

                    return new CsvStream(reader, finalHeaders);
                }))//
                .nameThrowingPredicate(Throwable::getMessage)//
                .prependFailureMessage("Axiom CSV Strike Failure [" + inputPath + "]: ");
    }


    final class CsvStream implements AutoCloseable {
        private final BufferedReader reader;
        private final PersistentList<String> headers;
        private String nextLine = null;
        private boolean finished = false;

        private CsvStream(BufferedReader reader, PersistentList<String> headers) {
            this.reader = reader;
            this.headers = headers;
        }

        public static CsvStream empty() {
            final var emptyStream = new CsvStream(null, Axiom.Data.emptyList());
            emptyStream.finished = true;
            return emptyStream;
        }

        public boolean hasNextRow() {
            if (finished) return false;
            if (nextLine != null) return true;
            if (reader == null) return false;

            try {
                while ((nextLine = reader.readLine()) != null) {
                    if (!nextLine.isBlank()) {
                        return true;
                    }
                }
                finished = true;
                close();
                return false;
            } catch (Exception e) {
                finished = true;
                try { close(); } catch (Exception ignored) {}
                throw new RuntimeException("CSV Line Read Breach", e);
            }
        }

        public PersistentMap<String, Object> nextRow() {
            if (!hasNextRow()) {
                  throw  Axiom.Check.failure("CSV Stream Exhausted").failureValue();
            }
            try {
                final var lineToProcess = nextLine;
                nextLine = null;
                return CsvRowParser.takeLine(lineToProcess).basedOnHeaders(headers);
            } catch (Exception e) {
                throw Axiom.Check.failure("CSV Parsing Breach: " + e.getMessage()).failureValue();
            }
        }

        @Override
        public void close() throws java.io.IOException {
            if (reader != null) {
                reader.close();
            }
        }

        public long count() {
            try (this) {
                long total = 0;
                while (hasNextRow()) {
                    nextRow();
                    total++;
                }
                return total;
            } catch (Exception e) {
                throw new RuntimeException("CSV Stream Consumption Failure", e);
            }
        }

        public Result<PersistentMap<String, Object>> head() {
            try (this) {
                return hasNextRow()
                        ? Axiom.Check.success(nextRow())
                        : Axiom.Check.failure("CSV Stream is empty");
            } catch (Exception e) {
                return Axiom.Check.failure("CSV Stream Head Breach: " + e.getMessage());
            }
        }
    }
}