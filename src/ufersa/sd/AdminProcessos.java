package ufersa.sd;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import ufersa.sd.contrato.Processo;
import ufersa.sd.enums.RecursoId;

/**
 * Singleton de estado da lista de pares conhecidos pela instância
 */
public enum AdminProcessos {
    INSTANCE();

    private Processo processoUnid;
    private List<Processo> ListaProcessos;
    private Boolean iniciado = false;
    private Map<Instant, Processo> recursoRequerido1;
    private Map<Instant, Processo> recursoRequerido2;

    AdminProcessos() {
        this.ListaProcessos = new LinkedList<>();
        try {
            KeyPair chaveProcesso = Seguranca.generateRSA();
            this.processoUnid = new Processo(chaveProcesso.getPublic(), chaveProcesso.getPrivate());
            System.err.println("Entre com Seu Processo ID: " + this.processoUnid.getId());
            this.recursoRequerido1 = new TreeMap<Instant, Processo>();
            this.recursoRequerido2 = new TreeMap<Instant, Processo>();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Processo getProcessoUnid() {
        return this.processoUnid;
    }

    public void add(Processo p) {
        this.ListaProcessos.add(p);
        System.err.println("Adicionado processo para lista: " + p.getId());
        atualizacaoIniciada(this.ListaProcessos);
    }

    public void remover(Processo p) {
        this.ListaProcessos.remove(p);
        System.err.println("Removendo processo da lista: " + p.getId());
    }

    public void exibirProcessosLista() {
        if (this.ListaProcessos.size() == 0)
            System.out.println("\nA lista está vázia\n");
        else {
            System.out.println("\nProcessos na lista: \n");
            for (Processo processo : this.ListaProcessos) {
                System.out.println(processo.getId() + "\n");
            }
        }
    }

    public List<Processo> getListaProcessos() {
        return ListaProcessos;
    }

    public void atualizacaoIniciada(List<Processo> listaProcessos) {
        if (!iniciado && listaProcessos.size() + 1 >= Configuracao.ELEMENTOS_MINIMOS)
            iniciado = true;
    }

    public Map<Instant, Processo> getRecursoRequerido(RecursoId recurso) {
        if (recurso.equals(RecursoId.RECURSO1))
            return recursoRequerido1;
        return recursoRequerido2;
    }

    public boolean isIniciado() {
        return iniciado;
    }
}
