package ro.licenta.bluetoothcontrollerapp.enums;

/**
 * Created by Eduard.Neagoe on 19-May-17.
 */

public enum SerialData{
    LIGHT_ON("1"),
    LIGHT_OFF("0");

    private String value;

    SerialData(String value) {
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }
}