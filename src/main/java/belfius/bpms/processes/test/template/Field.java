package belfius.bpms.processes.test.template;

public class Field {
    private String fieldName;
    private String fieldType;


    public Field(String fieldName, String fieldType) {
        super();
        this.fieldName = fieldName;
        this.fieldType = fieldType;
    }

    public String getFieldName() {
        return fieldName;
    }
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    public String getFieldType() {
        return fieldType;
    }
    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }
}