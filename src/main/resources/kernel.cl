// kernel.cl - OpenCL Kernel para contagem de palavras

/**
 * Kernel principal para contagem de palavras
 * @param text - Array de caracteres contendo o texto
 * @param textLength - Comprimento total do texto
 * @param searchWord - Palavra a ser procurada
 * @param searchLength - Comprimento da palavra de busca
 * @param wordBoundaries - Array indicando início de cada palavra
 * @param numWords - Número total de palavras
 * @param results - Array de resultados (um por work-item)
 */
__kernel void countWords(__global const char* text,
                        const int textLength,
                        __global const char* searchWord,
                        const int searchLength,
                        __global const int* wordBoundaries,
                        const int numWords,
                        __global int* results) {
    
    int gid = get_global_id(0);
    int localCount = 0;
    
    // Cada work-item processa uma palavra
    if (gid < numWords) {
        int wordStart = wordBoundaries[gid];
        int wordEnd = (gid < numWords - 1) ? wordBoundaries[gid + 1] : textLength;
        int wordLength = wordEnd - wordStart;
        
        // Verificar se o comprimento é compatível
        if (wordLength == searchLength) {
            bool match = true;
            
            // Comparar caractere por caractere
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

/**
 * Kernel otimizado para textos grandes usando redução local
 */
__kernel void countWordsOptimized(__global const char* text,
                                 const int textLength,
                                 __global const char* searchWord,
                                 const int searchLength,
                                 __global const int* wordBoundaries,
                                 const int numWords,
                                 __global int* results,
                                 __local int* localResults) {
    
    int gid = get_global_id(0);
    int lid = get_local_id(0);
    int localSize = get_local_size(0);
    int localCount = 0;
    
    // Cada work-item processa uma palavra
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
    
    // Armazenar resultado local
    localResults[lid] = localCount;
    barrier(CLK_LOCAL_MEM_FENCE);
    
    // Redução paralela no grupo de trabalho
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (lid < stride) {
            localResults[lid] += localResults[lid + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    
    // O primeiro work-item do grupo escreve o resultado final
    if (lid == 0) {
        results[get_group_id(0)] = localResults[0];
    }
}

/**
 * Kernel simples para processamento de texto sem pré-processamento
 * Menos eficiente mas mais simples de usar
 */
__kernel void countWordsSimple(__global const char* text,
                              const int textLength,
                              __global const char* searchWord,
                              const int searchLength,
                              __global int* result) {
    
    int gid = get_global_id(0);
    
    if (gid <= textLength - searchLength) {
        // Verificar se estamos no início de uma palavra
        bool isWordStart = (gid == 0) || 
                          (text[gid - 1] == ' ' || text[gid - 1] == '\t' || 
                           text[gid - 1] == '\n' || text[gid - 1] == '\r' ||
                           text[gid - 1] == '.' || text[gid - 1] == ',' ||
                           text[gid - 1] == ';' || text[gid - 1] == ':' ||
                           text[gid - 1] == '!' || text[gid - 1] == '?');
        
        if (isWordStart) {
            bool match = true;
            
            // Verificar se a palavra corresponde
            for (int i = 0; i < searchLength; i++) {
                if (text[gid + i] != searchWord[i]) {
                    match = false;
                    break;
                }
            }
            
            // Verificar se é o fim da palavra
            if (match && (gid + searchLength >= textLength || 
                         text[gid + searchLength] == ' ' || 
                         text[gid + searchLength] == '\t' ||
                         text[gid + searchLength] == '\n' || 
                         text[gid + searchLength] == '\r' ||
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

/**
 * Kernel para pré-processamento do texto
 * Identifica boundaries de palavras
 */
__kernel void findWordBoundaries(__global const char* text,
                                const int textLength,
                                __global int* wordBoundaries,
                                __global int* wordCount) {
    
    int gid = get_global_id(0);
    
    if (gid < textLength) {
        bool isWordChar = (text[gid] >= 'a' && text[gid] <= 'z') ||
                         (text[gid] >= 'A' && text[gid] <= 'Z') ||
                         (text[gid] >= '0' && text[gid] <= '9');
        
        bool prevIsWordChar = false;
        if (gid > 0) {
            prevIsWordChar = (text[gid - 1] >= 'a' && text[gid - 1] <= 'z') ||
                            (text[gid - 1] >= 'A' && text[gid - 1] <= 'Z') ||
                            (text[gid - 1] >= '0' && text[gid - 1] <= '9');
        }
        
        // Início de palavra: caractere de palavra após não-palavra (ou início do texto)
        if (isWordChar && !prevIsWordChar) {
            int index = atomic_inc(wordCount);
            wordBoundaries[index] = gid;
        }
    }
}

/**
 * Kernel para conversão para minúsculas
 * Útil para busca case-insensitive
 */
__kernel void toLowerCase(__global char* text, const int textLength) {
    int gid = get_global_id(0);
    
    if (gid < textLength) {
        if (text[gid] >= 'A' && text[gid] <= 'Z') {
            text[gid] = text[gid] + ('a' - 'A');
        }
    }
}

/**
 * Kernel de redução final para somar resultados parciais
 */
__kernel void reduceResults(__global const int* partialResults,
                           const int numResults,
                           __global int* finalResult,
                           __local int* localData) {
    
    int gid = get_global_id(0);
    int lid = get_local_id(0);
    int localSize = get_local_size(0);
    
    // Carregar dados para memória local
    localData[lid] = (gid < numResults) ? partialResults[gid] : 0;
    barrier(CLK_LOCAL_MEM_FENCE);
    
    // Redução paralela
    for (int stride = localSize / 2; stride > 0; stride >>= 1) {
        if (lid < stride) {
            localData[lid] += localData[lid + stride];
        }
        barrier(CLK_LOCAL_MEM_FENCE);
    }
    
    // Escrever resultado final
    if (lid == 0) {
        atomic_add(finalResult, localData[0]);
    }
}