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
compile 'fr.coppernic.sdk.core:CpcCore:1.4.0'
```
#### Power on/off RFID reader

* Implements power listener

```java

public class MainActivity extends AppCompatActivity implements PowerListener {
  // [...]
  @Override
  public void onPowerUp(RESULT res, Peripheral peripheral) {
    if (res == RESULT.OK) {
      //Peripheral is on
    } else {
      //Peripehral is undefined
    }
  }

  @Override
  public void onPowerDown(RESULT res, Peripheral peripheral) {
      //Peripheral is off
  }
  // [...]
}

```

 * Register the listener

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    PowerManager.get().registerListener(this);
}
```

 * Power reader on

```java
// Powers on HID HF reader
ConePeripheral.RFID_HID_MULTIISO_GPIO.on(context);
// The listener will be called with the result
```

 * Power off when you are done

```java
// Powers off HID HF reader
ConePeripheral.RFID_HID_MULTIISO_GPIO.off(context);
// The listener will be called with the result
```

 * release resources

```java
@Override
protected void onStop() {
    PowerManager.get().unregisterAll();
    PowerManager.get().releaseResources();
    super.onDestroy();
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
