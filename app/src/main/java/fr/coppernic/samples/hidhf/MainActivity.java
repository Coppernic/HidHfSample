package fr.coppernic.samples.hidhf;

import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.ScrollingTabContainerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fr.coppernic.sdk.powermgmt.PowerMgmt;
import fr.coppernic.sdk.powermgmt.PowerMgmtFactory;
import fr.coppernic.sdk.powermgmt.cone.identifiers.InterfacesCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.ManufacturersCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.ModelsCone;
import fr.coppernic.sdk.powermgmt.cone.identifiers.PeripheralTypesCone;
import fr.coppernic.sdk.serial.SerialCom;
import fr.coppernic.sdk.serial.SerialFactory;
import fr.coppernic.sdk.utils.io.InstanceListener;

public class MainActivity extends AppCompatActivity implements InstanceListener<SerialCom> {
    private static final String TAG = "MainActivity";
    private static final String SERIAL_PORT = "/dev/ttyHSL1";

    private static final byte[] GET_FIRMWARE_COMMAND = new byte[]{'v'};
    private static final byte[] SELECT_COMMAND = new byte[]{'s'};
    private static final byte[] CONTINUOUS_MODE_COMMAND = new byte[]{'c'};
    private static final byte[] ABORT_CONTINUOUS_READ_COMMAND = new byte[]{'.'};
    private static final byte[] ENABLE_ISSO1443A = new byte[]{'o', 'a'};

    // Power Management
    private PowerMgmt powerMgmt;

    // Serial port
    private String portName;
    private SerialCom serialCom;

    // UI
    private Switch swPower;
    private Switch swOpen;
    private ArrayAdapter<String> adapter;
    private ArrayAdapter<String> portsAdapter;
    @BindView(R.id.etCommand) public EditText etCommand;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        // Build Power management object
        powerMgmt = PowerMgmtFactory.get()
                .setContext(this)
                .setPeripheralTypes(PeripheralTypesCone.RfidSc)
                .setManufacturers(ManufacturersCone.Hid)
                .setModels(ModelsCone.MultiIso)
                .setInterfaces(InterfacesCone.ExpansionPort)
                .build();

        // UI
        swPower = (Switch)findViewById(R.id.swPower);
        swPower.setOnCheckedChangeListener(onSwPowerCheckedChanged);
        swOpen = (Switch)findViewById(R.id.swOpen);
        swOpen.setOnCheckedChangeListener(onSwOpenCheckedChanged);
        // Firmware version button
        findViewById(R.id.btnFirmwareVersion).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(GET_FIRMWARE_COMMAND);
            }
        });
        // Select button
        findViewById(R.id.btnSelect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(SELECT_COMMAND);
            }
        });

        // Continuous Mode button
        findViewById(R.id.btnContinuousMode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(CONTINUOUS_MODE_COMMAND);
            }
        });

        // Abort continuous mode
        findViewById(R.id.btnAbortContinuousRead).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(ABORT_CONTINUOUS_READ_COMMAND);
            }
        });

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ListView lvLogs = (ListView)findViewById(R.id.lvLogs);
        lvLogs.setAdapter(adapter);

        // Reader communication
        SerialFactory.getDirectInstance(this, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_clear:
                adapter.clear();
                adapter.notifyDataSetChanged();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private CompoundButton.OnCheckedChangeListener onSwPowerCheckedChanged = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                powerMgmt.powerOn();
            } else {
                powerMgmt.powerOff();
            }
        }
    };

    private CompoundButton.OnCheckedChangeListener onSwOpenCheckedChanged = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (serialCom.open(portName, getBaudrate()) != SerialCom.ERROR_OK) {
                    addLog("Error open com");
                } else {
                    serialCom.setRts(true);
                    addLog("Open com OK");
                    // Starts listening thread
                    new Thread(listeningThread).start();
                    // By default after powered up reader is in continuous reading
                    // Stops continuous reading
                    sendCommand(ABORT_CONTINUOUS_READ_COMMAND);
                }
            } else {
                serialCom.close();
            }
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        if (swOpen.isChecked()) {
            swOpen.setChecked(false);
        }

        if (swPower.isChecked()) {
            swPower.setChecked(false);
        }
    }

    @Override
    public void onCreated(SerialCom serialCom) {
        // Serial instance is obtained
        this.serialCom = serialCom;
        String[] devices = this.serialCom.listDevices();

        portName = SERIAL_PORT;

        ArrayList<String> ports = new ArrayList<>();
        ports.add(SERIAL_PORT);

        for (String s:devices) {
            if (s.contains("USB")) {
                ports.add(s);
            }
        }

        portsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ports);
        final Spinner spPorts = (Spinner)findViewById(R.id.spPorts);
        spPorts.setAdapter(portsAdapter);
        spPorts.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                portName = spPorts.getSelectedItem().toString();
                Log.d(TAG, "Selected port: " + portName);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        swOpen.setEnabled(true);
    }

    @Override
    public void onDisposed(SerialCom serialCom) {

    }

    /**
     * Gets baudrate from Spinner
     * @return Baudrate
     */
    private int getBaudrate() {
        Spinner spBaudrates = (Spinner)findViewById(R.id.spBaudrates);
        return Integer.parseInt(spBaudrates.getSelectedItem().toString());
    }

    private void sendCommand(final byte[] command) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                serialCom.send(command, command.length);
            }
        }).start();
    }

    /**
     * Listens for serial port data
     */
    private Runnable listeningThread = new Runnable() {
        @Override
        public void run() {
            while (serialCom.isOpened()) {
                int availableBytes;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((availableBytes = serialCom.getQueueStatus()) > 0) {
                    byte[] bytesRead = new byte[availableBytes];
                    serialCom.receive(100, availableBytes, bytesRead);
                    try {
                        baos.write(bytesRead);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    SystemClock.sleep(5);
                }

                if (baos.size() > 0) {
                    addLog(new String(baos.toByteArray()));
                }
            }
        }
    };

    /**
     * Displays a log message in the logs list view
     * @param s Message to be displayed
     */
    private void addLog(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String[] str = s.split("\n");
                for (String s:str) {
                    adapter.insert(s, 0);
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    @OnClick(R.id.btnSend)
    public void sendCustomCommand() {
        String command = etCommand.getText().toString();
        sendCommand(command.getBytes());
    }
}
