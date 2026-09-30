package com.tanidikvar.api.evaluation.dto;
public enum EvaluationCriterion {
 GENERAL("Genel memnuniyet"), EDUCATION("Eğitim kalitesi"), ACADEMIC_STAFF("Akademik kadro"), CAMPUS("Kampüs ve sosyal yaşam"), TRANSPORT("Ulaşım ve konum"), HOUSING("Yurt ve barınma"), CAREER("Kariyer olanakları"), STUDENT_SERVICES("Öğrenci işleri");
 private final String label;
 EvaluationCriterion(String label){this.label=label;}
 public String label(){return label;}
}
