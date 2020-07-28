package ufersa.sd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import ufersa.sd.contrato.Mensagem;
import ufersa.sd.contrato.Processo;
import ufersa.sd.enums.RecursoId;
import ufersa.sd.enums.EstadoRecurso;

/**
 * Classe de troca de mensagens da instância
 */
class HandlerMensagem {
    private final AsyncMessagesHandler asyncMessagesHandler;
    private AtomicReference<MulticastSocket> mSocket;
    private AtomicReference<Instant> UltimaSolicitacaoRecurso;
    private List<Mensagem> requisicaoRespostas;

    HandlerMensagem() throws IOException {
        mSocket = new AtomicReference<>();
        mSocket.set(new MulticastSocket(Configuracao.PORTA_PADRAO));
        asyncMessagesHandler = new AsyncMessagesHandler();
        UltimaSolicitacaoRecurso = new AtomicReference<Instant>();
    }

    /**
     * Método para enviar mensagem via socket Multicast
     * @param datagramSocket
     * @param mensagem
     */
    private static void envioMensagem(MulticastSocket datagramSocket, Mensagem mensagem) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            
            objectOutputStream.writeObject(mensagem);
            
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            
            DatagramPacket pacoteData = new DatagramPacket(byteArray, byteArray.length, 
            		InetAddress.getByName(Configuracao.GRUPO_MULTICAST), Configuracao.PORTA_PADRAO);
            
            datagramSocket.send(pacoteData);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Método de inicialização do fluxo onde:
     * - o socket se registra no grupo multicast;
     * - inicia a thread de tratamento assíncrono de mensagens;
     * - manda mensagem de cumprimento a todos os processos.
     *
     * @throws IOException
     */
    void iniciar() throws IOException {
        this.mSocket.get().joinGroup(InetAddress.getByName(Configuracao.GRUPO_MULTICAST));
        this.asyncMessagesHandler.start();
        this.cumprimento();
    }

    /**
     * Método de encerramento do fluxo onde:
     * - manda mensagem de adeus a todos os processos;
     * - interrompe a thread de tratamento assíncrono de mensagens;
     * - o socket sai do grupo multicast;
     * - o socket multicast é encerrado.
     */
    void fechar() throws IOException {
        this.mSocket.get().leaveGroup(InetAddress.getByName(Configuracao.GRUPO_MULTICAST));
        this.mSocket.get().close();
    }

    /**
     * método que trata a liberação de um recurso.
     */
    public void recursoLiberado(RecursoId recursoId) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.TipoDeMensagem.RECURSO_LIBERAR, 
        		AdminProcessos.INSTANCE.getProcessoUnid(), recursoId));
    }

    /**
     * método que trata a requisição de um request.
     */
    public void recursoRequisicao(RecursoId recursoID) {
        // Atualiza o estado desse par sobre esse recurso para REQUISITADO
        AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(recursoID, EstadoRecurso.WANTED);

        UltimaSolicitacaoRecurso.set(Instant.now());

        requisicaoRespostas = new LinkedList<Mensagem>();

        // Envia uma mensagem de requisição do recurso
        mRecursoRequisitado(recursoID, UltimaSolicitacaoRecurso.get());

        while (requisicaoRespostas.size() != AdminProcessos.INSTANCE.getListaProcessos().size()) {
        	//Segundos entre requisição e resposta
            if (ChronoUnit.SECONDS.between(UltimaSolicitacaoRecurso.get(), Instant.now()) >= Configuracao.DELTA_TEMPO) { 
                List<Processo> paresRespostasRecebidas = new LinkedList<Processo>();
                List<Processo> paresNRespostasRecebidas = AdminProcessos.INSTANCE.getListaProcessos();
                for (Mensagem mgs : requisicaoRespostas) {
                    paresRespostasRecebidas.add(mgs.getParDestino());
                }
                
                //removendo da lista de pares aqueles que responderam nós temos os que não responderam
                for (Processo p : paresRespostasRecebidas) {
                    paresNRespostasRecebidas.remove(p); 
                }
                // agora removemos esses processos
                for (Processo p : paresNRespostasRecebidas) {
                    envioMensagem(this.mSocket.get(), new Mensagem(Mensagem.TipoDeMensagem.REQUISICAO_DEIXAR, p));
                }
                break;
            }
        }
        // Excluimos todos os ausentes, agora vereficamos as respostas
        Boolean liberarAreas = requisicaoRespostas.stream().allMatch(requisicaoRespostas -> requisicaoRespostas.getSituacao().equals(EstadoRecurso.RELEASE));
        if (liberarAreas) {
            // Se todos responderam released, esse processo pode pegar o recurso
            AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(recursoID, EstadoRecurso.HELD);
            putOnQueue(recursoID, UltimaSolicitacaoRecurso.get());
            System.out.println("Mudando " + recursoID + " para MANTIDO");
          //Se algum está em MANTIDO
        } else if (requisicaoRespostas.stream().anyMatch(respostaRequisicao -> respostaRequisicao.getSituacao().equals(EstadoRecurso.HELD))) { 
            System.out.println("\nO Recurso é utilizado por um outro processo, coloque Solicitação na fila");
            
          //Se teve uma batalha de menor tempo de indicação, o que perdeu 
          //vai vir como liberada porque liberou, entao tem que mudar pra procurada novamente
            AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(recursoID, EstadoRecurso.WANTED);
            putOnQueue(recursoID, UltimaSolicitacaoRecurso.get());
        }
    }

    /**
     * método que constrói e envia mensagem de cumprimento aos processos.
     */
    private void cumprimento() {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.TipoDeMensagem.REQUISICAO_CUMPRI, 
        				AdminProcessos.INSTANCE.getProcessoUnid()));
    }

    /**
     * método que constrói e envia mensagem de mudança na fila de um resource.
     */
    private void mFilaAdiciona(RecursoId recursoID, Instant timestamp) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.TipoDeMensagem.FILA_ADD, 
        				AdminProcessos.INSTANCE.getProcessoUnid(), 
        				recursoID, timestamp));
    }

    /**
     * método que constrói e envia mensagem de saída aos processos.
     */
    void fechamento() {
        if (AdminProcessos.INSTANCE.getListaProcessos().size() == 0) {
            try {
                this.fechar();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            this.liberaFila(RecursoId.RECURSO1);
            this.liberaFila(RecursoId.RECURSO2);
            envioMensagem(this.mSocket.get(), 
            		new Mensagem(Mensagem.TipoDeMensagem.REQUISICAO_DEIXAR, 
            				AdminProcessos.INSTANCE.getProcessoUnid()));
        }
    }

    public void liberaFila(RecursoId recursoID) {
        Map<Instant, Processo> filaRecursos = AdminProcessos.INSTANCE.getRecursoRequerido(recursoID);
        Map.Entry<Instant, Processo> Principal;
        
        //Verifica se n ta vazia
        if (!filaRecursos.isEmpty()) { 
            Iterator<Map.Entry<Instant, Processo>> iterator = filaRecursos.entrySet().iterator();
            
            //Pega nova cabeça da fila
            Principal = iterator.next();
            Processo processo = AdminProcessos.INSTANCE.getProcessoUnid();
            
            //Se o cabeça da fila for o gente, muda pra MANTIDO
            if (Principal.getValue().equals(processo)) { 
                this.recursoLiberado(recursoID);
            } else {
                while (iterator.hasNext()) {
                    Map.Entry<Instant, Processo> i = iterator.next();

                    // está no corpo da fila como interessado
                    if(i.getValue().equals(processo)) {
                        envioMensagem(mSocket.get(), 
                        		new Mensagem(Mensagem.TipoDeMensagem.FILA_REMOVE, 
                        				AdminProcessos.INSTANCE.getProcessoUnid(),
                        				recursoID, i.getKey()));
                    }
                }
            }
        }
    }

    /**
     * método que constrói e envia mensagem de requisição de recurso aos processos.
     */
    private void mRecursoRequisitado(RecursoId recursoID, Instant timestamp) {
        envioMensagem(this.mSocket.get(), 
        		new Mensagem(Mensagem.TipoDeMensagem.REQUISICAO_RECURSO, 
        				AdminProcessos.INSTANCE.getProcessoUnid(), 
        				recursoID, timestamp, AdminProcessos.INSTANCE.getProcessoUnid().getAssinatura()));
    }

    /**
     * método que verifica se n teve 2 processos requisitando o recurso ao mesmo instante.
     */
    private void putOnQueue(RecursoId recursoID, Instant timestamp) {
    	
    	//Se não é duplicado, adiciona sem problemas
        if (!AdminProcessos.INSTANCE.getRecursoRequerido(recursoID).containsKey(timestamp)) 
        //Coloca na fila Se ja tem um lá
        	AdminProcessos.INSTANCE.getRecursoRequerido(recursoID).put(timestamp, AdminProcessos.INSTANCE.getProcessoUnid());
        else {
            timestamp = timestamp.plus(1, ChronoUnit.NANOS);
            AdminProcessos.INSTANCE.getRecursoRequerido(recursoID).put(timestamp, AdminProcessos.INSTANCE.getProcessoUnid());
        }
        
      //avisa outros pares para atualizarem sua fila
        mFilaAdiciona(recursoID, UltimaSolicitacaoRecurso.get());
    }

    /**
     * Handler assíncrono de eventos no grupo multicast.
     */
    class AsyncMessagesHandler extends Thread {
    	
        @Override
        public void run() {
            try {
            	//criando o buffer de 
                byte[] buffer = new byte[4096];
                
                // enquanto o socket não for fechado no foreground, rode...
                while (!mSocket.get().isClosed()) { 
                	
                	 // cria referencia do pacote
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
                    
                    // recebe dados dentro de pacote
                    mSocket.get().receive(pacote);
                    
                    // armazena bytes do pacote dentro de array
                    byte[] data = pacote.getData();
                    // cria fluxo de bytes
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data); 
                    
                    // cria input stream de objeto
                    ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream); 
                    
                    // converte array de bytes do pacote em objeto
                    Mensagem mensagemRecebida = (Mensagem) objectInputStream.readObject(); 

                    System.err.println("Mensagem recebida: " + mensagemRecebida);

                    // se a mensagem capturada é do própria instancia, pule
                    if (mensagemRecebida.processoOrigem.equals(AdminProcessos.INSTANCE.getProcessoUnid()))
                        continue;
                    
                    // verifica tipo da mensagem
                    switch (mensagemRecebida.tipoMensagem) { 
                    	
                    	// mensagem de requisição de cumprimento
                        case REQUISICAO_CUMPRI: { 
                            handleRequisicaoSaudacao(mensagemRecebida);
                            break;
                        }
                        // mensagem de resposta cumprimento
                        case RESPOSTA_CUMPRI: { 
                            handleRespostaSaudacao(mensagemRecebida);
                            break;
                        }
                        // mensagem de "saindo - deixando" do par
                        case REQUISICAO_DEIXAR: {
                            handleLeaveRequest(mensagemRecebida);
                            break;
                        }
                        case RESPOSTA_DEIXAR: {
                            handleLeaveResponse(mensagemRecebida);
                            break;
                        }
                        // mensagem de requisição de recurso
                        case REQUISICAO_RECURSO: {
                            handleResourceRequest(mensagemRecebida);
                            break;
                        }
                        // mensagem de resposta a req. de recurso
                        case REQUISICAO_RESPOSTA: { 
                            handleResourceResponse(mensagemRecebida);
                            break;
                        }
                        //mensagem para liberar recurso
                        case RECURSO_LIBERAR: {
                            handleResourceRelease(mensagemRecebida);
                            break;
                        }
                        //mensagem para adicionar na fila requisição de recurso
                        case FILA_ADD: {
                            handleFilaAdd(mensagemRecebida);
                            break;
                        }
                        //mensagem para remover da fila requisição de recurso
                        case FILA_REMOVE: {
                            handleFilaRemove(mensagemRecebida);
                            break;
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        
        // se mensagem é destinada a esta instância adicione quem mandou a mensagem.
        private void handleResourceResponse(Mensagem mensagemRecebida) {
            if (!mensagemRecebida.processoDestino.equals(AdminProcessos.INSTANCE.getProcessoUnid())) 
                return;

            System.out.println("Resposta recebida de " + mensagemRecebida.processoOrigem);
            requisicaoRespostas.add(mensagemRecebida);
        }

        private void handleResourceRequest(Mensagem mensagemRecebida) {
            if (!verifySignature(mensagemRecebida)) return;
            
            // Guarda qual dos dois recursos é o que requisitor
            RecursoId recursoRequisitado = mensagemRecebida.getRecurso();
            //verifica em que situação o estado está para este processo
            EstadoRecurso EstadoRequisicaoRecurso = 
            		AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().get(recursoRequisitado); 
            // envia mensagem de resposta a requisição
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.TipoDeMensagem.REQUISICAO_RESPOSTA, 
            		AdminProcessos.INSTANCE.getProcessoUnid(), mensagemRecebida.processoOrigem, recursoRequisitado, 
            		EstadoRequisicaoRecurso, Instant.now())); 
            System.out.println("RECURSO_REQUISITADO para  " + recursoRequisitado + "\n");
        }

        private boolean verifySignature(Mensagem mensagemRecebida) {
            Processo ParProp = mensagemRecebida.processoOrigem;
            Optional<Processo> primeiro = AdminProcessos.INSTANCE.getListaProcessos().stream().filter(par -> par.getId().equals(ParProp.getId())).findFirst();
            PublicKey chavePubValida = primeiro.get().getChavePub();
            try {
                Seguranca.checkSignature(chavePubValida, mensagemRecebida.assinatura);
                System.err.println(String.format("assinatura válida:  %s ", mensagemRecebida.processoOrigem.getId()));
            } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                System.err.println(String.format("Assinatura inválida : %s ", mensagemRecebida.processoOrigem.getId()));
                return false;
            }
            return true;
        }

        private void handleResourceRelease(Mensagem mensagemRecebida) {
        	// Guarda qual dos dois recursos é o liberado
            RecursoId recursoRequisitado = mensagemRecebida.getRecurso();
            
            //Pega o cabeça da fila
            Map.Entry<Instant, Processo> Principal = AdminProcessos.INSTANCE.getRecursoRequerido(recursoRequisitado).entrySet().iterator().next();
            
            //Remove o cabeça da fila que tinha o recurso
            AdminProcessos.INSTANCE.getRecursoRequerido(recursoRequisitado).remove(Principal.getKey());
            
            //Verifica se n ta vazia
            if (!AdminProcessos.INSTANCE.getRecursoRequerido(recursoRequisitado).isEmpty()) { 
            	
            	//Pega novo cabeça da fila
                Principal = AdminProcessos.INSTANCE.getRecursoRequerido(recursoRequisitado).entrySet().iterator().next();
                
                //Se o cabeça da fila for a gente, muda pra HELD
                if (Principal.getValue().equals(AdminProcessos.INSTANCE.getProcessoUnid())) { 
                    AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(recursoRequisitado, EstadoRecurso.HELD);
                    System.out.println("Está agora com " + recursoRequisitado);
                }
            }
        }

        private void handleLeaveRequest(Mensagem mensagemRecebida) {
            // remova o par da lista de processos online
            AdminProcessos.INSTANCE.remover(mensagemRecebida.processoOrigem);
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.TipoDeMensagem.RESPOSTA_DEIXAR, 
            		AdminProcessos.INSTANCE.getProcessoUnid(), 
            		mensagemRecebida.processoOrigem));
        }

        private void handleLeaveResponse(Mensagem mensagensRecebidas) {
            if (!mensagensRecebidas.processoDestino.equals(AdminProcessos.INSTANCE.getProcessoUnid())) return;
            	AdminProcessos.INSTANCE.getListaProcessos().remove(mensagensRecebidas.processoOrigem);
            	
            if (AdminProcessos.INSTANCE.getListaProcessos().size() == 0) {
                try {
                    fechar();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleRespostaSaudacao(Mensagem mensagemRecebida) {
            // se mensagem é destinada a esta instância adicione quem mandou a mensagem.
            if (!mensagemRecebida.processoDestino.equals(AdminProcessos.INSTANCE.getProcessoUnid())) return;
            	AdminProcessos.INSTANCE.add(mensagemRecebida.processoOrigem);
        }

        
        private void handleRequisicaoSaudacao(Mensagem mensagemRecebida) {
            AdminProcessos.INSTANCE.add(mensagemRecebida.processoOrigem);
            
            // envia mensagem de auto-apresentação destinada ao novo processo
            envioMensagem(mSocket.get(), new Mensagem(Mensagem.TipoDeMensagem.RESPOSTA_CUMPRI, 
            		AdminProcessos.INSTANCE.getProcessoUnid(), 
            		mensagemRecebida.processoOrigem)); 
        }

        private void handleFilaAdd(Mensagem mensagemRecebida) {
        	// Guarda sobre qual dos dois recursos é a msg
            RecursoId recurso = mensagemRecebida.getRecurso();
            // Qual par enviou a msg
            Processo par = mensagemRecebida.getParOrigem();
            //Qual timestamp
            Instant time = mensagemRecebida.getTimestamp(); 
            //adiciona a fila
            AdminProcessos.INSTANCE.getRecursoRequerido(recurso).put(time, par);
        }

        private void handleFilaRemove(Mensagem mensagemRecebida) {
        	// Guarda sobre qual dos dois recursos é a msg
            RecursoId recurso = mensagemRecebida.getRecurso();
            // Qual par enviou a msg
            Processo par = mensagemRecebida.getParOrigem();
            //Qual timestamp
            Instant time = mensagemRecebida.getTimestamp();
            //adiciona a fila
            AdminProcessos.INSTANCE.getRecursoRequerido(recurso).remove(time, par);
        }
    }
}