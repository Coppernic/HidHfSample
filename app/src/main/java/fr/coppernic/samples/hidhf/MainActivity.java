package fr.coppernic.samples.hidhf;

import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;

import java.util.ArrayList;

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
    private static final String SERIAL_PORT = "/dev/ttyHSL1";

    private static final byte[] GET_FIRMWARE_COMMAND = new byte[]{'v'};
    private static final byte[] SELECT_COMMAND = new byte[]{'s'};
    private static final byte[] CONTINUOUS_MODE_COMMAND = new byte[]{'c'};
    private static final byte[] ABORT_CONTINUOUS_READ_COMMAND = new byte[]{'.'};

    // Power Management
    private PowerMgmt powerMgmt;

    // Serial port
    private SerialCom serialCom;

    // UI
    private Switch swPower;
    private Switch swOpen;
    ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                if (serialCom.open(SERIAL_PORT, getBaudrate()) != SerialCom.ERROR_OK) {
                    addLog("Error open com");
                } else {
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
                if (serialCom.getQueueStatus() > 0) {
                    int availableBytes = serialCom.getQueueStatus();
                    byte[] bytesRead = new byte[availableBytes];
                    serialCom.receive(100, availableBytes, bytesRead);
                    addLog(new String(bytesRead));
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
}
