import java.io.*;
import java.util.List;

public class CSVGenerator {
    
    public static void generateCSV(List<PerformanceAnalyzer.TestResult> results, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // Cabeçalho
            writer.println("TextFile,SearchWord,Sample,Method,Count,ExecutionTime");
            
            // Dados
            for (PerformanceAnalyzer.TestResult result : results) {
                writer.printf("%s,%s,%d,%s,%d,%d%n",
                    result.textFile, result.searchWord, result.sample,
                    result.method, result.count, result.executionTime);
            }
            
            System.out.println("Arquivo CSV gerado: " + filename);
            
        } catch (IOException e) {
            System.err.println("Erro ao gerar CSV: " + e.getMessage());
        }
    }
}