package ufersa.sd.contrato;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import ufersa.sd.Seguranca;
import ufersa.sd.enums.RecursoId;
import ufersa.sd.enums.EstadoRecurso;

import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("serial")
public class Processo implements Serializable {
    private String id;
    private PublicKey chavePub;
    private transient PrivateKey chavePri;
    private Map<RecursoId, EstadoRecurso> recursoSituacaoAtual;
    private byte[] assinatura;

    //adicionando segurança quanto a validação de entrada
    public Processo(PublicKey chavePub, PrivateKey chavePri) {
        this.id = UUID.randomUUID().toString().substring(0, 4);
        this.chavePub = chavePub;
        this.chavePri = chavePri;
        this.recursoSituacaoAtual = new HashMap<RecursoId, EstadoRecurso>();

        byte[] idBytes = this.id.getBytes();
        try {
            this.assinatura = Seguranca.sign(this.chavePri, idBytes);
        } catch (InvalidKeyException | 
        		NoSuchPaddingException | 
        		IllegalBlockSizeException | 
        		BadPaddingException | 
        		NoSuchAlgorithmException e) 
        {	
            e.printStackTrace();
        }

        //Adiciona estados iniciais de cada um dos 3 recursos
        this.recursoSituacaoAtual.put(RecursoId.RECURSO1,
                EstadoRecurso.RELEASE);
        this.recursoSituacaoAtual.put(RecursoId.RECURSO2,
                EstadoRecurso.RELEASE);
    }

    public PublicKey getChavePub() {
        return chavePub;
    }

    public String getId() {
        return id;
    }

    public Map<RecursoId, EstadoRecurso> getRecursoIniciado() {
        return recursoSituacaoAtual;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Processo processo = (Processo) o;
        return Objects.equals(id, processo.id) &&
                Objects.equals(chavePub, processo.chavePub);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, chavePub, chavePri);
    }

    @Override
    public String toString() {
        return String.format("Processo { id = '%s'}", id);
    }

    public byte[] getAssinatura() {
        return assinatura;
    }
}
