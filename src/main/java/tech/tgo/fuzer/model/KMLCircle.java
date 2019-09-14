package tech.tgo.fuzer.model;

import org.w3c.dom.Element;

/**
 *
 * @author
 */
public class KMLCircle
{
    public Element measurementLine;
    public Element measCircleCoords;

    public KMLCircle(Element ring, Element coords)
    {
        measurementLine = ring;
        measCircleCoords = coords;
    }
}