package org.jobscraper.jobscraper;

public class JobOffer {
    private final String title;
    private final String company;
    private final String salary;
    private final String location;
    private final String url;
    private final String typeOfWork;
    private final String experience;
    private final String operatingMode;

    public JobOffer(String title, String company, String salary, String location, String url, String typeOfWork, String experience, String operatingMode) {
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.location = location;
        this.url = url;
        this.typeOfWork = typeOfWork;
        this.experience = experience;
        this.operatingMode = operatingMode;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getSalary() {
        return salary;
    }

    public String getLocation() {
        return location;
    }

    public String getUrl() {
        return url;
    }

    public String getTypeOfWork() {
        return typeOfWork;
    }

    public String getExperience() {
        return experience;
    }

    public String getOperatingMode() {
        return operatingMode;
    }

    @Override
    public String toString() {
        return "JobOffer{" +
                "title='" + title + '\'' +
                ", company='" + company + '\'' +
                ", salary='" + salary + '\'' +
                ", location='" + location + '\'' +
                ", url='" + url + '\'' +
                ", typeOfWork='" + typeOfWork + '\'' +
                ", experience='" + experience + '\'' +
                ", operatingMode='" + operatingMode + '\'' +
                '}';
    }
}