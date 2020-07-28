# SD-Multicast-Ricart-Agrawala
Projeto sistema distribuído - implementação do algoritmo de exclusão mútua - Ricart Agrawala

1. Implemente um algoritmo para exclusão mútua distribuída.
* Algoritmo distribuído (Ricart-Agrawala);
* Protocolo de nível de aplicação para executar uma seção crítica:
entrar(): verifica a possibilidade de entrar na seção crítica (bloqueia, se necessário).
◦ acessarRecurso(): acessa recurso compartilhado.
* Considere um arquivo em que processos fazem operações de leitura e escrita.
*liberarRecurso(): sair da seção crítica.
* Requisitos básicos que o algoritmo deve ter:
* Segurança: no máximo um processo por vez pode ser executado na seção crítica.
* Imparcialidade: evitar inanição de processos.
* Detecção de falhas: processos devem detectar quando algum outro está em estado de
falha (Ver o material sobre tolerância a falhas).
* Escolha uma estratégia de detecção de falha para implementar.
* Número mínimo de processos para testar o algoritmo: 4.
