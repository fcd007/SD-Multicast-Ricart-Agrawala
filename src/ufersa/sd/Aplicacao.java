package ufersa.sd;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Scanner;

import ufersa.sd.contrato.Processo;
import ufersa.sd.enums.RecursoId;
import ufersa.sd.enums.EstadoRecurso;

public class Aplicacao {
    private HandlerMensagem mensagensHandler;

    public Aplicacao() throws IOException {
        this.mensagensHandler = new HandlerMensagem();
    }

    public static void main(String[] args) {
        try {
            Aplicacao aplicacao = new Aplicacao();
            aplicacao.iniciar();
            aplicacao.cli();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void iniciar() {
        try {
            this.mensagensHandler.iniciar();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void cli() throws IOException {
        Scanner scanner = new Scanner(System.in);
        boolean fecharPrograma = false;
        String solicitacao;
        do {
            System.out.println("\nSolicitação:");
            System.out.println("lista");
            System.out.println("recurso1");
            System.out.println("recurso2");
            
            //Se processo tem posse do recurso 1
            if(isRecursoRetido(RecursoId.RECURSO1)) 
                System.out.println("Livre recurso1");
            
            //Se processo tem posse do recurso 2
            if(isRecursoRetido(RecursoId.RECURSO2))
                System.out.println("Livre recurso2");
            System.out.println("fim");
            System.out.println("Tipo da seleção: ");
            
            solicitacao = scanner.nextLine();

            switch (solicitacao.trim().toLowerCase()) {
                case "lista": {
                    AdminProcessos.INSTANCE.exibirProcessosLista();
                    break;
                }
                case "recurso1": {
                    if (!AdminProcessos.INSTANCE.isIniciado())
                        System.out.println("Minímo " + Configuracao.ELEMENTOS_MINIMOS + " processos para iniciar ");
                    else {
                        this.mensagensHandler.recursoRequisicao(RecursoId.RECURSO1);
                    }
                    break;
                }
                case "recurso2": {
                    if (!AdminProcessos.INSTANCE.isIniciado())
                        System.out.println("Minímo " + Configuracao.ELEMENTOS_MINIMOS + " processos para iniciar ");
                    else {
                        this.mensagensHandler.recursoRequisicao(RecursoId.RECURSO2);
                    }
                    break;
                }
                case "livre recurso1": {
                	
                	//Checa se processo realmente tem posse do recurso01
                    if(isRecursoRetido(RecursoId.RECURSO1)) { 
                        AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(RecursoId.RECURSO1, EstadoRecurso.RELEASE);
                        
                        //Pega o cabeça da fila que tem o recurso
                        Map.Entry <Instant,Processo> Principal = AdminProcessos.INSTANCE.getRecursoRequerido(RecursoId.RECURSO1).entrySet().iterator().next();
                        
                        //Remove ele proprio da cabeça da fila
                        AdminProcessos.INSTANCE.getRecursoRequerido(RecursoId.RECURSO1).remove(Principal.getKey());
                        
                        //avisar os outros por multicast que liberou
                        this.mensagensHandler.recursoLiberado(RecursoId.RECURSO1);
                    }
                    else {
                        System.out.println("\nVocê não tem o recurso1 para liberar!\n");
                    }
                    break;
                }
                case "livre recurso02": {
                	//Checa processo realmente tem posse do recurso02
                    if(isRecursoRetido(RecursoId.RECURSO2)) { 
                        AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().put(RecursoId.RECURSO2, EstadoRecurso.RELEASE);
                        
                        //Pega o cabeça da fila que tem o recurso
                        Map.Entry <Instant,Processo> Principal = 
                        		AdminProcessos.INSTANCE.getRecursoRequerido(RecursoId.RECURSO2).entrySet().iterator().next();
                      //Remove ele proprio da cabeça da fila
                        AdminProcessos.INSTANCE.getRecursoRequerido(RecursoId.RECURSO2).remove(Principal.getKey());
                      //avisar os outros por multicast que liberou
                        this.mensagensHandler.recursoLiberado(RecursoId.RECURSO2);
                    }
                    else {
                        System.out.println("\nVocê não possui o recurso2 para liberar\n");
                    }
                    break;
                }
                case "fim": {
                    this.close();
                    fecharPrograma = true;
                    break;
                }
                default:
                    System.out.println("seleção inválida ou desconhecida.");
                    break;
            }
        } while (!fecharPrograma);
        
        //fechar recurso
        scanner.close();
    }

    private void close() throws IOException {
        this.mensagensHandler.fechamento();
    }

    private boolean isRecursoRetido(RecursoId recursoId){
    	//Se processo tem posse do recurso
        if(AdminProcessos.INSTANCE.getProcessoUnid().getRecursoIniciado().get(recursoId) == EstadoRecurso.HELD) 
            return true;
        return false;
    }
}
