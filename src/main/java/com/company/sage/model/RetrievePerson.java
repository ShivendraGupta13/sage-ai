package com.company.sage.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Person details associated with retrieve hit metadata.
 */
public class RetrievePerson {

    private String personId;
    private String name;

    public RetrievePerson() {
    }

    public RetrievePerson(String personId, String name) {
        this.personId = personId;
        this.name = name;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
