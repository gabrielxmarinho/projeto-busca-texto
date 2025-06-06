import org.jocl.*;
import static org.jocl.CL.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class OpenCLWordCounter {
    
    private cl_context context;
    private cl_command_queue queue;
    private cl_device_id device;
    private cl_program program;
    
    public OpenCLWordCounter() {
        initializeOpenCL();
    }
    
    private void initializeOpenCL() {
        try {
            // Habilitar exceções
            CL.setExceptionsEnabled(true);
            
            // Obter plataforma
            cl_platform_id[] platforms = new cl_platform_id[1];
            clGetPlatformIDs(1, platforms, null);
            
            // Obter dispositivo GPU (se disponível), senão CPU
            cl_device_id[] devices = new cl_device_id[1];
            try {
                clGetDeviceIDs(platforms[0], CL_DEVICE_TYPE_GPU, 1, devices, null);
                System.out.println("Usando GPU");
            } catch (Exception e) {
                System.out.println("GPU não disponível, usando CPU");
                clGetDeviceIDs(platforms[0], CL_DEVICE_TYPE_CPU, 1, devices, null);
            }
            device = devices[0];
            
            // Criar contexto e fila de comandos
            context = clCreateContext(null, 1, devices, null, null, null);
            queue = clCreateCommandQueue(context, device, 0, null);
            
            // Carregar e compilar kernels
            String kernelSource = loadKernelSource();
            program = clCreateProgramWithSource(context, 1, 
                    new String[]{kernelSource}, null, null);
            
            int buildResult = clBuildProgram(program, 0, null, null, null, null);
            if (buildResult != CL_SUCCESS) {
                // Obter log de build em caso de erro
                long[] logSize = new long[1];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, 0, null, logSize);
                byte[] log = new byte[(int)logSize[0]];
                clGetProgramBuildInfo(program, device, CL_PROGRAM_BUILD_LOG, logSize[0], Pointer.to(log), null);
                System.err.println("Erro de build: " + new String(log));
                throw new RuntimeException("Falha ao compilar kernels");
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao inicializar OpenCL: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    public int countWords(String text, String searchWord) {
        try {
            return countWordsOptimized(text, searchWord);
        } catch (Exception e) {
            System.err.println("Erro no processamento GPU, tentando método simples: " + e.getMessage());
            e.printStackTrace();
            return countWordsSimple(text, searchWord);
        }
    }
    
    private int countWordsOptimized(String text, String searchWord) {
        // Converter para minúsculas
        text = text.toLowerCase();
        searchWord = searchWord.toLowerCase();
        
        // Pré-processar: encontrar palavras usando regex mais precisa
        String[] words = text.split("[\\s\\p{Punct}]+");
        List<String> validWords = new ArrayList<>();
        List<Integer> wordPositions = new ArrayList<>();
        
        // Encontrar posições das palavras válidas
        int currentPos = 0;
        for (String word : words) {
            if (!word.isEmpty()) {
                // Encontrar a posição real da palavra no texto
                int wordPos = text.indexOf(word, currentPos);
                if (wordPos >= 0) {
                    validWords.add(word);
                    wordPositions.add(wordPos);
                    currentPos = wordPos + word.length();
                }
            }
        }
        
        if (validWords.isEmpty()) {
            return 0;
        }
        
        // Converter para arrays
        int[] wordBoundaries = wordPositions.stream().mapToInt(i -> i).toArray();
        int[] wordLengths = validWords.stream().mapToInt(String::length).toArray();
        
        // Criar buffers OpenCL
        byte[] textBytes = text.getBytes();
        byte[] searchBytes = searchWord.getBytes();
        
        cl_mem textBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_char * textBytes.length, 
            Pointer.to(textBytes), null);
            
        cl_mem searchBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_char * searchBytes.length, 
            Pointer.to(searchBytes), null);
            
        cl_mem boundariesBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_int * wordBoundaries.length, 
            Pointer.to(wordBoundaries), null);
            
        cl_mem lengthsBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_int * wordLengths.length, 
            Pointer.to(wordLengths), null);
        
        // Buffer para resultados (um por palavra)
        int[] results = new int[validWords.size()];
        cl_mem resultsBuffer = clCreateBuffer(context, 
            CL_MEM_WRITE_ONLY,
            Sizeof.cl_int * results.length, null, null);
        
        // Criar e configurar kernel
        cl_kernel kernel = clCreateKernel(program, "countWords", null);
        
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textBuffer));
        clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{textBytes.length}));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(searchBuffer));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{searchBytes.length}));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(boundariesBuffer));
        clSetKernelArg(kernel, 5, Sizeof.cl_mem, Pointer.to(lengthsBuffer));
        clSetKernelArg(kernel, 6, Sizeof.cl_int, Pointer.to(new int[]{validWords.size()}));
        clSetKernelArg(kernel, 7, Sizeof.cl_mem, Pointer.to(resultsBuffer));
        
        // Executar kernel
        long[] globalWorkSize = {validWords.size()};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        
        // Aguardar conclusão
        clFinish(queue);
        
        // Ler resultados
        clEnqueueReadBuffer(queue, resultsBuffer, CL_TRUE, 0, 
            Sizeof.cl_int * results.length, Pointer.to(results), 0, null, null);
        
        // Somar resultados
        int totalCount = 0;
        for (int count : results) {
            totalCount += count;
        }
        
        // Debug: mostrar palavras encontradas
        System.out.printf("Debug: Processando %d palavras, buscando '%s'%n", validWords.size(), searchWord);
        for (int i = 0; i < validWords.size(); i++) {
            if (results[i] > 0) {
                System.out.printf("  Encontrada: '%s' na posição %d%n", validWords.get(i), wordBoundaries[i]);
            }
        }
        
        // Limpeza
        clReleaseMemObject(textBuffer);
        clReleaseMemObject(searchBuffer);
        clReleaseMemObject(boundariesBuffer);
        clReleaseMemObject(lengthsBuffer);
        clReleaseMemObject(resultsBuffer);
        clReleaseKernel(kernel);
        
        return totalCount;
    }
    
    private int countWordsSimple(String text, String searchWord) {
        System.out.println("Usando método simples de fallback");
        
        // Converter para minúsculas
        text = text.toLowerCase();
        searchWord = searchWord.toLowerCase();
        
        byte[] textBytes = text.getBytes();
        byte[] searchBytes = searchWord.getBytes();
        
        // Criar buffers
        cl_mem textBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_char * textBytes.length, 
            Pointer.to(textBytes), null);
            
        cl_mem searchBuffer = clCreateBuffer(context, 
            CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_char * searchBytes.length, 
            Pointer.to(searchBytes), null);
        
        // Buffer para resultado único - inicializar com zero
        int[] result = new int[]{0};
        cl_mem resultBuffer = clCreateBuffer(context, 
            CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
            Sizeof.cl_int, Pointer.to(result), null);
        
        // Criar kernel simples
        cl_kernel kernel = clCreateKernel(program, "countWordsSimple", null);
        
        clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(textBuffer));
        clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{textBytes.length}));
        clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(searchBuffer));
        clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{searchBytes.length}));
        clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(resultBuffer));
        
        // Executar kernel
        long[] globalWorkSize = {Math.max(1, textBytes.length - searchBytes.length + 1)};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        
        // Aguardar conclusão
        clFinish(queue);
        
        // Ler resultado
        clEnqueueReadBuffer(queue, resultBuffer, CL_TRUE, 0, 
            Sizeof.cl_int, Pointer.to(result), 0, null, null);
        
        // Limpeza
        clReleaseMemObject(textBuffer);
        clReleaseMemObject(searchBuffer);
        clReleaseMemObject(resultBuffer);
        clReleaseKernel(kernel);
        
        return result[0];
    }
    
    private String loadKernelSource() {
        try {
            // Tentar carregar do arquivo
            if (Files.exists(Paths.get("src/main/resources/kernel.cl"))) {
                return new String(Files.readAllBytes(Paths.get("src/main/resources/kernel.cl")));
            }
        } catch (IOException e) {
            System.out.println("Não foi possível carregar kernel.cl, usando versão embarcada");
        }
        
        // Versão embarcada como fallback
        return getEmbeddedKernelSource();
    }
    
    private String getEmbeddedKernelSource() {
        return """
            __kernel void countWords(__global const char* text,
                                    const int textLength,
                                    __global const char* searchWord,
                                    const int searchLength,
                                    __global const int* wordBoundaries,
                                    __global const int* wordLengths,
                                    const int numWords,
                                    __global int* results) {
                
                int gid = get_global_id(0);
                
                if (gid < numWords) {
                    int wordStart = wordBoundaries[gid];
                    int wordLength = wordLengths[gid];
                    
                    // Verificar se os comprimentos coincidem
                    if (wordLength == searchLength) {
                        bool match = true;
                        // Comparar caractere por caractere
                        for (int i = 0; i < searchLength; i++) {
                            if (wordStart + i >= textLength || text[wordStart + i] != searchWord[i]) {
                                match = false;
                                break;
                            }
                        }
                        results[gid] = match ? 1 : 0;
                    } else {
                        results[gid] = 0;
                    }
                } else {
                    results[gid] = 0;
                }
            }
            
            __kernel void countWordsSimple(__global const char* text,
                                          const int textLength,
                                          __global const char* searchWord,
                                          const int searchLength,
                                          __global int* result) {
                
                int gid = get_global_id(0);
                
                if (gid <= textLength - searchLength) {
                    // Verificar se estamos no início de uma palavra
                    bool isWordStart = (gid == 0) || 
                                      (text[gid - 1] == ' ' || text[gid - 1] == '\\t' || 
                                       text[gid - 1] == '\\n' || text[gid - 1] == '\\r' ||
                                       text[gid - 1] == '.' || text[gid - 1] == ',' ||
                                       text[gid - 1] == ';' || text[gid - 1] == ':' ||
                                       text[gid - 1] == '!' || text[gid - 1] == '?' ||
                                       text[gid - 1] == '(' || text[gid - 1] == ')' ||
                                       text[gid - 1] == '[' || text[gid - 1] == ']' ||
                                       text[gid - 1] == '{' || text[gid - 1] == '}');
                    
                    if (isWordStart) {
                        // Verificar se a palavra coincide
                        bool match = true;
                        for (int i = 0; i < searchLength; i++) {
                            if (text[gid + i] != searchWord[i]) {
                                match = false;
                                break;
                            }
                        }
                        
                        // Verificar se estamos no final de uma palavra
                        bool isWordEnd = (gid + searchLength >= textLength || 
                                         text[gid + searchLength] == ' ' || 
                                         text[gid + searchLength] == '\\t' ||
                                         text[gid + searchLength] == '\\n' || 
                                         text[gid + searchLength] == '\\r' ||
                                         text[gid + searchLength] == '.' || 
                                         text[gid + searchLength] == ',' ||
                                         text[gid + searchLength] == ';' || 
                                         text[gid + searchLength] == ':' ||
                                         text[gid + searchLength] == '!' || 
                                         text[gid + searchLength] == '?' ||
                                         text[gid + searchLength] == '(' || 
                                         text[gid + searchLength] == ')' ||
                                         text[gid + searchLength] == '[' || 
                                         text[gid + searchLength] == ']' ||
                                         text[gid + searchLength] == '{' || 
                                         text[gid + searchLength] == '}');
                        
                        if (match && isWordEnd) {
                            atomic_inc(result);
                        }
                    }
                }
            }
            """;
    }
    
    public void cleanup() {
        if (program != null) clReleaseProgram(program);
        if (queue != null) clReleaseCommandQueue(queue);
        if (context != null) clReleaseContext(context);
    }
    
    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }
    
    // Método de teste
    public static void main(String[] args) {
        try {
            OpenCLWordCounter counter = new OpenCLWordCounter();
            
            String testText = "The quick brown fox jumps over the lazy dog. The dog was very lazy.";
            String searchWord = "the";
            
            System.out.printf("Texto: '%s'%n", testText);
            System.out.printf("Buscando: '%s'%n", searchWord);
            
            long startTime = System.currentTimeMillis();
            int count = counter.countWords(testText, searchWord);
            long endTime = System.currentTimeMillis();
            
            System.out.printf("Resultado: %d ocorrências em %d ms%n", 
                             count, endTime - startTime);
            
            // Teste adicional com método Java puro para comparação
            String[] words = testText.toLowerCase().split("[\\s\\p{Punct}]+");
            int javaCount = 0;
            for (String word : words) {
                if (word.equals(searchWord.toLowerCase())) {
                    javaCount++;
                }
            }
            System.out.printf("Comparação Java: %d ocorrências%n", javaCount);
            
            counter.cleanup();
            
        } catch (Exception e) {
            System.err.println("Erro no teste: " + e.getMessage());
            e.printStackTrace();
        }
    }
}