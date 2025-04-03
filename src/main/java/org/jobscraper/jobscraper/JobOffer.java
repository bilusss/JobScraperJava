package org.jobscraper.jobscraper;

public class JobOffer {
    private String title;
    private String company;
    private String salary;
    private String location;
    private String url;

    public JobOffer(String title, String company, String salary, String location, String url) {
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.location = location;
        this.url = url;
    }

    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getSalary() { return salary; }
    public String getLocation() { return location; }
    public String getUrl() { return url; }

    @Override
    public String toString() {
        return "JobOffer{" +
                "title='" + title + '\'' +
                ", company='" + company + '\'' +
                ", salary='" + salary + '\'' +
                ", location='" + location + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
