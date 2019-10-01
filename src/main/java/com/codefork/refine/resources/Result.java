
package com.codefork.refine.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single name result. The JSON looks like this:
 * 
 * {
 *     'id' : '...',
 *     'name': '...',
 *     'type': [
 *         {
 *             "id": '...',
 *             "name": '...',
 *         },
 *     ],
 *     'score': 1,
 *     'match': true
 * }
 */
public class Result {
    
    private String id;
    private String name;

    /**
     * It's weird that this is a list but that's what OpenRefine expects.
     * A VIAF result only ever has one type, but maybe other reconciliation
     * services return multiple types for a name?
     */
    private List<NameType> type;
    private double score;
    private boolean match;
    private List<ObjectPV> pairPV;

    public Result() {
    }

    public Result(String id, String name, List<NameType> nameTypes, double score, boolean match) {
        this.id = id;
        this.name = name;
        this.type = nameTypes;
        this.score = score;
        this.match = match;
        this.pairPV = new ArrayList();
    }

    public Result(String id, String name, NameType nameType, double score, boolean match) {
        this(id, name, new ArrayList<>(Collections.singletonList(nameType)), score, match);
    }

    /**
     * This costructor sets score and match to -1 and false, respectively.
     * @param id the result id
     * @param name the result name
     * @param nameTypes the list of result NameTypes
     */
    public Result(String id, String name, List<NameType> nameTypes) {
        this(id, name, nameTypes, -1, false);
    }

    /**
     * This costructor sets score and match to -1 and false, respectively.
     * @param id the result id
     * @param name the result name
     * @param nameType the result NameType
     */
    public Result(String id, String name, NameType nameType) {
        this(id, name, nameType, -1, false);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<NameType> getType() {
        return type;
    }

    public void setType(List<NameType> resultType) {
        this.type = resultType;
    }

    public void addType(NameType nameType) { this.type.add(nameType); }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public List<ObjectPV> getPairPV() {
        return pairPV;
    }

    public void setPairPV(List<NameType> resultType) {
        this.pairPV = pairPV;
    }

    public void addPairPV(ObjectPV pairPV) { this.pairPV.add(pairPV); }

}
