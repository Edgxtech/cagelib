package tech.tgo.fuzer.util;

import javax.xml.transform.Transformer;
import org.w3c.dom.Document;

/**
 * @author Timothy Edge (timmyedge)
 */
public class ProvisionFilterStateExportDTO {
    Transformer transformer;
    Document doc;

    public ProvisionFilterStateExportDTO(Transformer transformer, Document doc) {
        this.transformer = transformer;
        this.doc = doc;
    }

    public Transformer getTransformer() {
        return transformer;
    }

    public void setTransformer(Transformer transformer) {
        this.transformer = transformer;
    }

    public Document getDoc() {
        return doc;
    }

    public void setDoc(Document doc) {
        this.doc = doc;
    }
}
