package com.brindysoft.andwebcam.model;

import android.location.Location;

import com.ibm.mobile.services.data.IBMDataObject;
import com.ibm.mobile.services.data.IBMDataObjectSpecialization;

import java.util.Date;

@IBMDataObjectSpecialization("WebCamImage")
public class WebCamImage extends IBMDataObject {

    public static final String DEVICE_ID = "device_id";
    public static final String LOCATION = "location";
    public static final String IMAGE_DATA = "image_data";
    public static final String DATE = "date";

    public WebCamImage() {
    }

    public WebCamImage(String deviceId) {
        setDeviceId(deviceId);
    }

    public void setDeviceId(String deviceId) {
        setObject(DEVICE_ID, deviceId);
    }

    public String getDeviceId() {
        return (String)getObject(DEVICE_ID);
    }

    public Location getLocation() {
        return (Location)getObject(LOCATION);
    }

    public void setLocation(Location location) {
        setObject(LOCATION, location);
    }

    public byte[] getImageData() {
        return (byte[])getObject(IMAGE_DATA);
    }

    public void setImageData(byte[] data) {
        setObject(IMAGE_DATA, data);
    }

    public void setDate(Date date) {
        setObject(DATE, date);
    }

    public Date getDate() {
        return (Date)getObject(DATE);
    }
}
