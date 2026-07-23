package com.company.sage.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Metadata fields associated with a retrieve hit.
 */
public class RetrieveHitMetadata {

    private String title;
    private String teamName = "N/A";
    private String teamId = "0";
    private List<RetrievePerson> people = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private String documentLink;
    private String category = "HARD_PROBLEMS";
    private String sourceAttribution = "Orion API";

    public RetrieveHitMetadata() {
    }

    public RetrieveHitMetadata(
            String title,
            String teamName,
            String teamId,
            List<RetrievePerson> people,
            List<String> technologies,
            String documentLink,
            String category,
            String sourceAttribution) {
        this.title = title;
        if (teamName != null) {
            this.teamName = teamName;
        }
        if (teamId != null) {
            this.teamId = teamId;
        }
        if (people != null) {
            this.people = people;
        }
        if (technologies != null) {
            this.technologies = technologies;
        }
        this.documentLink = documentLink;
        if (category != null) {
            this.category = category;
        }
        if (sourceAttribution != null) {
            this.sourceAttribution = sourceAttribution;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public List<RetrievePerson> getPeople() {
        return people;
    }

    public void setPeople(List<RetrievePerson> people) {
        if (people != null) {
            this.people = people;
        } else {
            this.people = new ArrayList<>();
        }
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<String> technologies) {
        if (technologies != null) {
            this.technologies = technologies;
        } else {
            this.technologies = new ArrayList<>();
        }
    }

    public String getDocumentLink() {
        return documentLink;
    }

    public void setDocumentLink(String documentLink) {
        this.documentLink = documentLink;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSourceAttribution() {
        return sourceAttribution;
    }

    public void setSourceAttribution(String sourceAttribution) {
        this.sourceAttribution = sourceAttribution;
    }
}
