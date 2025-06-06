# 📊 Análise de Desempenho de Algoritmos de Busca em Arquivos de Texto

![Java](https://img.shields.io/badge/language-Java-orange)
![License](https://img.shields.io/github/license/gabrielxmarinho/projeto-busca-texto)
![Status](https://img.shields.io/badge/status-finalizado-green)

## 🧾 Visão Geral

Este projeto compara o desempenho de diferentes abordagens para a busca de palavras em grandes arquivos de texto. Foram avaliadas três estratégias:

- 🔹 **Execução Sequencial (SerialCPU)**  
- 🔹 **Execução Paralela na CPU (ParallelCPU)**  
- 🔹 **Execução Paralela na GPU via JOCL (ParallelGPU)**

A palavra-chave utilizada nos testes foi `"the"`, aplicada a três obras literárias clássicas. Os resultados foram registrados e analisados por meio de gráficos e arquivos CSV.

---

## 🧭 Objetivo

Avaliar a eficiência e a acurácia das três abordagens de busca mencionadas, utilizando textos com tamanhos variados:

- 📘 *Moby Dick* (~217 mil palavras)
- 📗 *Dom Quixote* (~388 mil palavras)
- 📕 *Drácula* (~165 mil palavras)

---

## 🧪 Metodologia

1. 💻 Implementação dos algoritmos em Java
2. ⏱ Execução repetida com medições de tempo e contagem
3. 📂 Armazenamento dos dados em arquivos CSV
4. 📊 Geração de gráficos comparativos com os dados obtidos

---

## 📈 Resultados

| 📄 Obra               | ⚙️ Método      | 🔢 Ocorrências | ⏱ Tempo (ms) |
|----------------------|---------------|----------------|---------------|
| **Moby Dick**        | SerialCPU     | 14.715         | 102           |
|                      | ParallelCPU   | 14.715         | 91            |
|                      | ParallelGPU   | 14.512         | 1121          |
| **Dom Quixote**      | SerialCPU     | 188            | 141           |
|                      | ParallelCPU   | 188            | 128           |
|                      | ParallelGPU   | 186            | 1081          |
| **Drácula**          | SerialCPU     | 8.101          | 102           |
|                      | ParallelCPU   | 8.101          | 70            |
|                      | ParallelGPU   | 7.997          | 965           |

---

## 💬 Discussão

- ✅ **ParallelCPU** foi o método mais eficiente, com ganhos claros de performance e sem perda de precisão.
- 🐢 **SerialCPU** é confiável, porém naturalmente mais lenta.
- ⚠️ **ParallelGPU** apresentou inconsistências nas contagens e tempos de execução superiores à versão sequencial em alguns testes, sugerindo que o overhead de comunicação e limitações do modelo de paralelismo via OpenCL podem ter impactado negativamente o desempenho.

---

## 📊 Visualizações

### 🔽 Tempo de Execução por Método
![Gráfico de Performance - Tempo de Execução](./data/charts/performance_comparison.png)

### 📘 Moby Dick  
![Gráfico Moby Dick](./data/charts/MobyDick_chart.png)

### 📗 Don Quixote  
![Gráfico Don Quixote](./data/charts/DonQuixote_chart.png)

### 📕 Drácula  
![Gráfico Dracula](./data/charts/Dracula_chart.png)

---

## ✅ Conclusão

- **ParallelCPU** foi o destaque, conciliando rapidez e precisão.
- **SerialCPU** é adequada para cenários simples.
- **ParallelGPU**, embora promissora, exige refinamentos para entregar vantagem real neste tipo de tarefa textual.

---

## 📚 Referências

- [📘 Oracle Java Documentation](https://docs.oracle.com/javase/)
- [🚀 JOCL – Java bindings for OpenCL](http://www.jocl.org/)
- [📚 Project Gutenberg – Obras literárias](https://www.gutenberg.org/)

---

## 📎 Estrutura do Projeto

| Arquivo                  | Função                                                                 |
|--------------------------|------------------------------------------------------------------------|
| `PerformanceAnalyzer.java` | Coordena a execução dos testes                                       |
| `SearchStrategies.java`    | Implementa as versões Serial, Paralela na CPU e GPU                  |
| `CSVGenerator.java`        | Gera arquivos CSV com os resultados coletados                        |
| `ChartGenerator.java`      | Cria gráficos a partir dos dados dos testes                          |

---

## 📦 Repositório

🔗 Acesse o projeto completo no GitHub:  
**[github.com/gabrielxmarinho/projeto-busca-texto](https://github.com/gabrielxmarinho/projeto-busca-texto)**

---

🛠 Desenvolvido com foco em análise de performance e computação paralela para tarefas textuais.

