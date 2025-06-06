import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

public class SearchAlgorithms {
    
    /**
     * Método Serial CPU - Busca sequencial
     */
    public static SearchResult serialCPU(String text, String searchWord) {
        long startTime = System.currentTimeMillis();
        
        String[] words = text.toLowerCase().split("\\W+");
        int count = 0;
        
        for (String word : words) {
            if (word.equals(searchWord.toLowerCase())) {
                count++;
            }
        }
        
        long endTime = System.currentTimeMillis();
        return new SearchResult(count, endTime - startTime, "SerialCPU");
    }
    
    /**
     * Método Parallel CPU - Busca paralela usando threads
     */
    public static SearchResult parallelCPU(String text, String searchWord) {
        long startTime = System.currentTimeMillis();
        
        String[] words = text.toLowerCase().split("\\W+");
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        int chunkSize = words.length / numThreads;
        List<Future<Integer>> futures = new ArrayList<>();
        
        for (int i = 0; i < numThreads; i++) {
            final int start = i * chunkSize;
            final int end = (i == numThreads - 1) ? words.length : (i + 1) * chunkSize;
            
            Callable<Integer> task = () -> {
                int localCount = 0;
                for (int j = start; j < end; j++) {
                    if (words[j].equals(searchWord.toLowerCase())) {
                        localCount++;
                    }
                }
                return localCount;
            };
            
            futures.add(executor.submit(task));
        }
        
        int totalCount = 0;
        try {
            for (Future<Integer> future : futures) {
                totalCount += future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
        
        long endTime = System.currentTimeMillis();
        return new SearchResult(totalCount, endTime - startTime, "ParallelCPU");
    }
    
    /**
     * Método Parallel GPU - Busca paralela usando OpenCL
     */
    public static SearchResult parallelGPU(String text, String searchWord) {
        long startTime = System.currentTimeMillis();
        
        OpenCLWordCounter gpuCounter = new OpenCLWordCounter();
        int count = gpuCounter.countWords(text, searchWord);
        
        long endTime = System.currentTimeMillis();
        return new SearchResult(count, endTime - startTime, "ParallelGPU");
    }
    
    /**
     * Classe para armazenar resultados
     */
    public static class SearchResult {
        public final int count;
        public final long executionTime;
        public final String method;
        
        public SearchResult(int count, long executionTime, String method) {
            this.count = count;
            this.executionTime = executionTime;
            this.method = method;
        }
        
        @Override
        public String toString() {
            return String.format("%s: %d ocorrências em %d ms", method, count, executionTime);
        }
    }
    
    /**
     * Método para carregar texto de arquivo
     */
    public static String loadTextFromFile(String filepath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filepath)));
    }
    
    /**
     * Método principal para testes
     */
    public static void main(String[] args) {
    try {
        String textFile = "DonQuixote-388208.txt";
        String path = "src\\main\\resources\\sample_texts\\" + textFile;
        String text = loadTextFromFile(path);
        String searchWord = "the";
        int sample = 1;

        List<PerformanceAnalyzer.TestResult> resultados = new ArrayList<>();

        // Executar os três métodos e medir tempo
        long start = System.currentTimeMillis();
        SearchResult serialResult = serialCPU(text, searchWord);
        long serialTime = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        SearchResult parallelCpuResult = parallelCPU(text, searchWord);
        long parallelCpuTime = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        SearchResult parallelGpuResult = parallelGPU(text, searchWord);
        long parallelGpuTime = System.currentTimeMillis() - start;

        // Criar objetos TestResult
        resultados.add(new PerformanceAnalyzer.TestResult(textFile, searchWord, sample, "SerialCPU", serialResult.count, serialTime));
        resultados.add(new PerformanceAnalyzer.TestResult(textFile, searchWord, sample, "ParallelCPU", parallelCpuResult.count, parallelCpuTime));
        resultados.add(new PerformanceAnalyzer.TestResult(textFile, searchWord, sample, "ParallelGPU", parallelGpuResult.count, parallelGpuTime));

        // Exibir resultados
        System.out.println(serialResult);
        System.out.println(parallelCpuResult);
        System.out.println(parallelGpuResult);

        // Gerar CSV
        new File("data/results").mkdirs();
        CSVGenerator.generateCSV(resultados, "data/results/"+textFile+".csv");

    } catch (IOException e) {
        System.err.println("Erro ao carregar arquivo: " + e.getMessage());
    }
}

}