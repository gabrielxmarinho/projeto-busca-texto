import org.jocl.*;
import static org.jocl.CL.*;
import java.io.*;
import java.nio.file.*;

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
            clBuildProgram(program, 0, null, null, null, null);
            
        } catch (Exception e) {
            System.err.println("Erro ao inicializar OpenCL: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
    
    public int countWords(String text, String searchWord) {
        try {
            return countWordsOptimized(text, searchWord);
        } catch (Exception e) {
            System.err.println("Erro no processamento GPU, tentando método simples: " + e.getMessage());
            return countWordsSimple(text, searchWord);
        }
    }
    
    private int countWordsOptimized(String text, String searchWord) {
        // Converter para minúsculas
        text = text.toLowerCase();
        searchWord = searchWord.toLowerCase();
        
        // Pré-processar: encontrar boundaries das palavras
        String[] words = text.split("\\W+");
        int[] wordBoundaries = new int[words.length];
        int currentPos = 0;
        
        for (int i = 0; i < words.length; i++) {
            // Encontrar posição da palavra no texto original
            while (currentPos < text.length() && 
                   !Character.isLetterOrDigit(text.charAt(currentPos))) {
                currentPos++;
            }
            wordBoundaries[i] = currentPos;
            currentPos += words[i].length();
        }
        
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
        
        // Buffer para resultados (um por palavra)
        int[] results = new int[words.length];
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
        clSetKernelArg(kernel, 5, Sizeof.cl_int, Pointer.to(new int[]{words.length}));
        clSetKernelArg(kernel, 6, Sizeof.cl_mem, Pointer.to(resultsBuffer));
        
        // Executar kernel
        long[] globalWorkSize = {words.length};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        
        // Ler resultados
        clEnqueueReadBuffer(queue, resultsBuffer, CL_TRUE, 0, 
            Sizeof.cl_int * results.length, Pointer.to(results), 0, null, null);
        
        // Somar resultados
        int totalCount = 0;
        for (int count : results) {
            totalCount += count;
        }
        
        // Limpeza
        clReleaseMemObject(textBuffer);
        clReleaseMemObject(searchBuffer);
        clReleaseMemObject(boundariesBuffer);
        clReleaseMemObject(resultsBuffer);
        clReleaseKernel(kernel);
        
        return totalCount;
    }
    
    private int countWordsSimple(String text, String searchWord) {
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
        
        // Buffer para resultado único
        int[] result = new int[1];
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
        long[] globalWorkSize = {textBytes.length};
        clEnqueueNDRangeKernel(queue, kernel, 1, null, globalWorkSize, null, 0, null, null);
        
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
                                    const int numWords,
                                    __global int* results) {
                
                int gid = get_global_id(0);
                int localCount = 0;
                
                if (gid < numWords) {
                    int wordStart = wordBoundaries[gid];
                    int wordEnd = (gid < numWords - 1) ? wordBoundaries[gid + 1] : textLength;
                    int wordLength = wordEnd - wordStart;
                    
                    if (wordLength == searchLength) {
                        bool match = true;
                        for (int i = 0; i < searchLength; i++) {
                            if (text[wordStart + i] != searchWord[i]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            localCount = 1;
                        }
                    }
                }
                
                results[gid] = localCount;
            }
            
            __kernel void countWordsSimple(__global const char* text,
                                          const int textLength,
                                          __global const char* searchWord,
                                          const int searchLength,
                                          __global int* result) {
                
                int gid = get_global_id(0);
                
                if (gid <= textLength - searchLength) {
                    bool isWordStart = (gid == 0) || 
                                      (text[gid - 1] == ' ' || text[gid - 1] == '\\t' || 
                                       text[gid - 1] == '\\n' || text[gid - 1] == '\\r' ||
                                       text[gid - 1] == '.' || text[gid - 1] == ',' ||
                                       text[gid - 1] == ';' || text[gid - 1] == ':' ||
                                       text[gid - 1] == '!' || text[gid - 1] == '?');
                    
                    if (isWordStart) {
                        bool match = true;
                        for (int i = 0; i < searchLength; i++) {
                            if (text[gid + i] != searchWord[i]) {
                                match = false;
                                break;
                            }
                        }
                        
                        if (match && (gid + searchLength >= textLength || 
                                     text[gid + searchLength] == ' ' || 
                                     text[gid + searchLength] == '\\t' ||
                                     text[gid + searchLength] == '\\n' || 
                                     text[gid + searchLength] == '\\r' ||
                                     text[gid + searchLength] == '.' || 
                                     text[gid + searchLength] == ',' ||
                                     text[gid + searchLength] == ';' || 
                                     text[gid + searchLength] == ':' ||
                                     text[gid + searchLength] == '!' || 
                                     text[gid + searchLength] == '?')) {
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
        OpenCLWordCounter counter = new OpenCLWordCounter();
        
        String testText = "The quick brown fox jumps over the lazy dog. The dog was very lazy.";
        String searchWord = "the";
        
        long startTime = System.currentTimeMillis();
        int count = counter.countWords(testText, searchWord);
        long endTime = System.currentTimeMillis();
        
        System.out.printf("ParallelGPU: %d ocorrências em %d ms%n", 
                         count, endTime - startTime);
        
        counter.cleanup();
    }
}