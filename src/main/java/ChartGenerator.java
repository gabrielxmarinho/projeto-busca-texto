import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

public class ChartGenerator {
    
    public static void generateCharts(List<PerformanceAnalyzer.TestResult> results) {
        generatePerformanceComparisonChart(results);
        generateMethodComparisonChart(results);
    }
    
    private static void generatePerformanceComparisonChart(List<PerformanceAnalyzer.TestResult> results) {
        // Agrupar resultados por método
        Map<String, List<Long>> methodTimes = new HashMap<>();
        
        for (PerformanceAnalyzer.TestResult result : results) {
            methodTimes.computeIfAbsent(result.method, k -> new ArrayList<>())
                      .add(result.executionTime);
        }
        
        // Criar gráfico de barras
        JFrame frame = new JFrame("Comparação de Performance");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        ChartPanel panel = new ChartPanel(methodTimes);
        frame.add(panel);
        frame.setSize(800, 600);
        frame.setVisible(true);
        
        // Salvar como imagem
        saveChartAsImage(panel, "data/charts/performance_comparison.png");
    }
    
    private static void generateMethodComparisonChart(List<PerformanceAnalyzer.TestResult> results) {
        // Implementação similar para outros tipos de gráficos
        System.out.println("Gráficos gerados em data/charts/");
    }
    
    private static void saveChartAsImage(Component component, String filename) {
        try {
            BufferedImage image = new BufferedImage(component.getWidth(), 
                                                  component.getHeight(), 
                                                  BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();
            component.paint(g2d);
            g2d.dispose();
            
            File file = new File(filename);
            file.getParentFile().mkdirs();
            ImageIO.write(image, "PNG", file);
            
        } catch (IOException e) {
            System.err.println("Erro ao salvar gráfico: " + e.getMessage());
        }
    }
    
    private static class ChartPanel extends JPanel {
        private Map<String, List<Long>> data;
        
        public ChartPanel(Map<String, List<Long>> data) {
            this.data = data;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Desenhar gráfico de barras simples
            int barWidth = 60;
            int barSpacing = 20;
            int x = 50;
            int maxHeight = getHeight() - 100;
            
            // Encontrar valor máximo para escala
            double maxValue = data.values().stream()
                .flatMap(List::stream)
                .mapToLong(Long::longValue)
                .max().orElse(1);
            
            Color[] colors = {Color.BLUE, Color.GREEN, Color.RED};
            int colorIndex = 0;
            
            for (Map.Entry<String, List<Long>> entry : data.entrySet()) {
                String method = entry.getKey();
                double avgTime = entry.getValue().stream()
                    .mapToLong(Long::longValue)
                    .average().orElse(0);
                
                int barHeight = (int) ((avgTime / maxValue) * maxHeight);
                
                g2d.setColor(colors[colorIndex % colors.length]);
                g2d.fillRect(x, getHeight() - 50 - barHeight, barWidth, barHeight);
                
                g2d.setColor(Color.BLACK);
                g2d.drawString(method, x, getHeight() - 30);
                g2d.drawString(String.format("%.1f ms", avgTime), x, getHeight() - 60 - barHeight);
                
                x += barWidth + barSpacing;
                colorIndex++;
            }
        }
    }
}