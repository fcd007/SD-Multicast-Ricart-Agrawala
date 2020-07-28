package ufersa.sd.contrato;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import ufersa.sd.enums.RecursoId;
import ufersa.sd.enums.EstadoRecurso;

@SuppressWarnings("serial")
public class Mensagem implements Serializable {
    public Processo processoOrigem;
    public Processo processoDestino;
    public TipoDeMensagem tipoMensagem;
    public RecursoId recurso;
    public EstadoRecurso estado;
    public byte[] assinatura;

    //definindo a variável de timestamp
    Instant timestamp;

    public Mensagem(TipoDeMensagem tipoMensagem, Processo processoOrigem) {
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = processoOrigem;
    }

    public Mensagem(TipoDeMensagem tipoMensagem, Processo processoOrigem, Processo processoDestino) {
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = processoOrigem;
        this.processoDestino = processoDestino;
    }
    
    //Mensagem de requisição de recurso ou mudança na fila
    public Mensagem(
    		TipoDeMensagem tipoMensagem, 
    		Processo processoOrigem, 
    		RecursoId recurso, 
    		Instant timestamp) { 
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = processoOrigem;
        this.recurso = recurso;
        this.timestamp = timestamp;
    }

    //Mensagem de liberação de recurso
    public Mensagem(TipoDeMensagem tipoMensagem, Processo processoOrigem, RecursoId recurso) { 
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = processoOrigem;
        this.recurso = recurso;
    }

    //Mensagem de resposta a requisição de recurso
    public Mensagem(
    		TipoDeMensagem 
    		tipoMensagem, 
    		Processo processoOrigem, 
    		Processo processoDestino, 
    		RecursoId recurso, 
    		EstadoRecurso situacao, 
    		Instant timestamp) {
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = processoOrigem;
        this.processoDestino = processoDestino;
        this.recurso = recurso;
        this.estado = situacao;
        this.timestamp = timestamp;
    }

    //Mensagem de requisição de recurso ou mudança na fila
    public Mensagem(
    		TipoDeMensagem tipoMensagem, 
    		Processo parOrigem, 
    		RecursoId recurso, 
    		Instant timestamp, 
    		byte[] assinatura) { 
        this.tipoMensagem = tipoMensagem;
        this.processoOrigem = parOrigem;
        this.recurso = recurso;
        this.timestamp = timestamp;
        this.assinatura = assinatura;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mensagem messagem = (Mensagem) o;
        return Objects.equals(processoOrigem, messagem.processoOrigem) &&
                Objects.equals(processoDestino, messagem.processoDestino) &&
                tipoMensagem == messagem.tipoMensagem;
    }

    @Override
    public int hashCode() {
        return Objects.hash(processoOrigem, processoDestino, tipoMensagem);
    }

    @Override
    public String toString() {
        return String.format("Messagem { Processo Origem = %s, Processo Destino = %s, Tipo mensagem = %s }", processoOrigem, processoDestino, tipoMensagem);
    }

    public RecursoId getRecurso() {
        return recurso;
    }

    public Processo getParDestino() {
        return processoDestino;
    }

    public Processo getParOrigem() {
        return processoOrigem;
    }

    public EstadoRecurso getSituacao() {
        return estado;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public enum TipoDeMensagem {
        REQUISICAO_CUMPRI, 
        RESPOSTA_CUMPRI, 
        REQUISICAO_DEIXAR, 
        RESPOSTA_DEIXAR, 
        REQUISICAO_RECURSO, 
        REQUISICAO_RESPOSTA, 
        RECURSO_LIBERAR, 
        FILA_ADD, 
        FILA_REMOVE
    }
}
