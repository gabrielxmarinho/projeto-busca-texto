import java.io.*;
import java.util.*;

public class PerformanceAnalyzer {
    
    public static void runCompleteAnalysis() {
        String[] textFiles = {"small_text.txt", "medium_text.txt", "large_text.txt"};
        String[] searchWords = {"the", "and", "java", "performance"};
        int samplesPerTest = 3;
        
        List<TestResult> allResults = new ArrayList<>();
        
        for (String textFile : textFiles) {
            try {
                String text = SearchAlgorithms.loadTextFromFile("data/" + textFile);
                
                for (String searchWord : searchWords) {
                    System.out.println("Testando arquivo: " + textFile + ", palavra: " + searchWord);
                    
                    // Executar múltiplas amostras de cada método
                    for (int sample = 0; sample < samplesPerTest; sample++) {
                        // Serial CPU
                        SearchAlgorithms.SearchResult serialResult = 
                            SearchAlgorithms.serialCPU(text, searchWord);
                        allResults.add(new TestResult(textFile, searchWord, sample + 1, 
                            "SerialCPU", serialResult.count, serialResult.executionTime));
                        
                        // Parallel CPU
                        SearchAlgorithms.SearchResult parallelCpuResult = 
                            SearchAlgorithms.parallelCPU(text, searchWord);
                        allResults.add(new TestResult(textFile, searchWord, sample + 1,
                            "ParallelCPU", parallelCpuResult.count, parallelCpuResult.executionTime));
                        
                        // Parallel GPU
                        SearchAlgorithms.SearchResult parallelGpuResult = 
                            SearchAlgorithms.parallelGPU(text, searchWord);
                        allResults.add(new TestResult(textFile, searchWord, sample + 1,
                            "ParallelGPU", parallelGpuResult.count, parallelGpuResult.executionTime));
                        
                        System.out.println("  Amostra " + (sample + 1) + " concluída");
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao processar arquivo " + textFile + ": " + e.getMessage());
            }
        }
        
        // Gerar CSV com resultados
        CSVGenerator.generateCSV(allResults, "data/results.csv");
        
        // Gerar gráficos
        ChartGenerator.generateCharts(allResults);
        
        // Análise estatística
        performStatisticalAnalysis(allResults);
    }
    
    private static void performStatisticalAnalysis(List<TestResult> results) {
        Map<String, List<Long>> methodTimes = new HashMap<>();
        
        for (TestResult result : results) {
            methodTimes.computeIfAbsent(result.method, k -> new ArrayList<>())
                      .add(result.executionTime);
        }
        
        System.out.println("\n=== ANALISE ESTATISTICA ===");
        for (Map.Entry<String, List<Long>> entry : methodTimes.entrySet()) {
            String method = entry.getKey();
            List<Long> times = entry.getValue();
            
            double average = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long min = times.stream().mapToLong(Long::longValue).min().orElse(0);
            long max = times.stream().mapToLong(Long::longValue).max().orElse(0);
            
            System.out.printf("%s - Média: %.2f ms, Min: %d ms, Max: %d ms%n", 
                            method, average, min, max);
        }
    }
    
    public static class TestResult {
        public final String textFile;
        public final String searchWord;
        public final int sample;
        public final String method;
        public final int count;
        public final long executionTime;
        
        public TestResult(String textFile, String searchWord, int sample, 
                         String method, int count, long executionTime) {
            this.textFile = textFile;
            this.searchWord = searchWord;
            this.sample = sample;
            this.method = method;
            this.count = count;
            this.executionTime = executionTime;
        }
    }
}
