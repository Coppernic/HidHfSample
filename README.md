# hidhfsample

Introduction
------------

This application illustrates how to use the HID HF RFID reader on Coppernic C-One device.

The libraries
-------------

Coppernic uses a Maven repository to provide libraries.

In the build.gradle, at project level, add the following lines:

```groovy
allprojects {
    repositories {                
        maven { url 'https://artifactory.coppernic.fr/artifactory/libs-release'}
    }
}
```

The basics
----------
### Power management

#### Libraries
CpcCore is the library responsible for power management.

In your build.gradle file, at module level, add the following lines:

```groovy
compile 'fr.coppernic.sdk.core:CpcCore:1.3.0'
```
#### Power on/off RFID reader

First, create a Power management object:

``` groovy
private PowerMgmt powerMgmt;
```
Then instantiate it:

```groovy
powerMgmt = PowerMgmtFactory.get().setContext(context)
                .setPeripheralTypes(PeripheralTypesCone.RfidSc)
                .setManufacturers(ManufacturersCone.Hid)
                .setModels(ModelsCone.MultiIso)
                .setInterfaces(InterfacesCone.ExpansionPort)
                .build();
```
Finally, use the powerOn/powerOff methods:

```groovy
public void rfid (boolean on) {
    if (on) {
        powerMgmt.powerOn();
    } else {
        powerMgmt.powerOff();
    }
}
```

### Reader initialization
#### Libraries

Use the SerialCom class in CpcCore.


#### Create SerialCom object

First declare a SerialCom object:

```groovy
private SerialCom serialCom;
```

Then instantiate it:

```groovy
SerialFactory.getDirectInstance(this, this);
```

Where your activity implements InstanceListener<SerialCom>:

```groovy
@Override
public void onCreated(SerialCom serialCom) {
    // Serial instance is obtained
    this.serialCom = serialCom;      
}

@Override
public void onDisposed(SerialCom serialCom) {

}
```

### Open reader and send commands

First open the serialCom object:

```groovy
serialCom.open(SERIAL_PORT, getBaudrate());
```
where:

```groovy
private static final String SERIAL_PORT = "/dev/ttyHSL1";
```

Then send commands:

```groovy
byte[] command;
...
serialCom.send(command, command.length);
```
### Initialization

When powered up, reader is in continuous read mode. Before being able to use it, disable continuous read mode by sending a simple command:

```groovy
private static final byte[] ABORT_CONTINUOUS_READ_COMMAND = new byte[]{'.'};
```


```groovy
serialCom.send(ABORT_CONTINUOUS_READ_COMMAND, ABORT_CONTINUOUS_READ_COMMAND.length);
```
