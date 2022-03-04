package ro.licenta.bluetoothcontrollerapp;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;
import static ro.licenta.bluetoothcontrollerapp.enums.SerialData.LIGHT_OFF;
import static ro.licenta.bluetoothcontrollerapp.enums.SerialData.LIGHT_ON;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private ListView listViewPairedDevices;
    private Switch btSwitch, lightSwitch;
    private TextView textViewPairedDevices, textViewPleaseEnableBtCon, textViewDeviceConnected;
    private ProgressDialog progressDialog;

    private BluetoothDevice device;
    private OutputStream outputStream;
    private BluetoothSocket btSocket;
//    private static final UUID MY_UUID = UUID.fromString("0000110E-0000-1000-8000-00805F9B34FB");
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btSwitch = (Switch) findViewById(R.id.btSwitch);
        lightSwitch = (Switch) findViewById(R.id.lightSwitch);

        listViewPairedDevices = (ListView) findViewById(R.id.listViewPairedDevices);
        textViewPairedDevices = (TextView) findViewById(R.id.textViewPairedDevices);
        textViewPleaseEnableBtCon = (TextView) findViewById(R.id.textViewTurnBtOn);
        textViewDeviceConnected = (TextView) findViewById(R.id.textViewConnected);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        setVisibilityForViews(false, lightSwitch, textViewDeviceConnected);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(btStateChangedReceiver, filter);


        if(bluetoothAdapter.isEnabled()){
            btSwitch.setChecked(true);
            buildPairedDevicesList();
            setVisibilityForViews(true, listViewPairedDevices, textViewPairedDevices);
            setVisibilityForViews(false, textViewPleaseEnableBtCon);
        }else{
            setVisibilityForViews(false, listViewPairedDevices, textViewPairedDevices);
            setVisibilityForViews(true, textViewPleaseEnableBtCon);
        }

        btSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if(isChecked){
                enableBluetooth();
                showLoadingDialog(R.string.paired_devices_searching, R.string.wait);
            }else{
                disableBluetooth();
            }
            }
        });

        lightSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    sendData(LIGHT_ON.getValue());
                }else{
                    sendData(LIGHT_OFF.getValue());
                }
            }
        });

        listViewPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                String deviceName = "HC-05";
//                String deviceAddress = "98:D3:31:FD:28:AD";
                device = getSelectedDevice(position);
                btSocket = createSocket(device);
                bluetoothAdapter.cancelDiscovery();
                establishConnection();
            }
        });
    }

    private BluetoothDevice getSelectedDevice(int positionInList){
        String selectedFromList =(String) (listViewPairedDevices.getItemAtPosition(positionInList));
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice foundDevice = null;
        if (pairedDevices != null) {
            for (BluetoothDevice d : pairedDevices) {
                if (selectedFromList.equals(d.getName())) {
                    foundDevice = d;
                    break;
                }
            }
        }
        return foundDevice;
    }

    private BluetoothSocket createSocket(BluetoothDevice device){
        try {
            if (device != null) {
                BluetoothSocket btSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                return btSocket;
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        return null;
    }

    private void establishConnection(){
        if (btSocket != null ) {
            try {
                outputStream = btSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        btSocket.connect();
                        showToastMessage(R.string.connected);
                    } catch (IOException e) {
//                        showAlert(R.string.oops, R.string.interrupted, R.string.ok); //title, message, neutral button
                        showToastMessage(R.string.interrupted);
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    private void closeSocket(){
        try {
            btSocket.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private void sendData(String message){
        if(outputStream == null){
            try {
                outputStream = btSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        byte[] messageBuffer = message.getBytes();

        try {
            outputStream.write(messageBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(btStateChangedReceiver);
        closeSocket();
    }


    private BroadcastReceiver btStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action){
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    final int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (btState) {
                        case STATE_TURNING_ON:
                            btSwitch.setChecked(true);
                            setVisibilityForViews(false, textViewPleaseEnableBtCon);
                            break;

                        case STATE_ON:
                            buildPairedDevicesList();
                            dismissLoadingDialog();
                            setVisibilityForViews(true, listViewPairedDevices, textViewPairedDevices);
                            break;

                        case STATE_TURNING_OFF:
                            btSwitch.setChecked(false);
                            setVisibilityForViews(false, listViewPairedDevices, textViewPairedDevices, lightSwitch, textViewDeviceConnected);
                            setVisibilityForViews(true, textViewPleaseEnableBtCon);
                            break;
                    }
                    break;
                }

                case BluetoothDevice.ACTION_ACL_CONNECTED:{
                    setVisibilityForViews(true, lightSwitch, textViewDeviceConnected);
                    setVisibilityForViews(false, listViewPairedDevices, textViewPairedDevices);
                    break;
                }

                case BluetoothDevice.ACTION_ACL_DISCONNECTED:{
                    setVisibilityForViews(false, lightSwitch, textViewDeviceConnected);
                    break;
                }
            }
        }
    };

    public void enableBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
    }

    public void disableBluetooth(){
        if(bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        }
    }

    public void buildPairedDevicesList() {
        if (listViewPairedDevices.getAdapter() == null || listViewPairedDevices.getAdapter().isEmpty()) {

            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

            List<String> devicesNames = new ArrayList<>();

            for (BluetoothDevice btd : pairedDevices) {
                devicesNames.add(btd.getName());
            }
            final ListAdapter adapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, devicesNames);
            listViewPairedDevices.setAdapter(adapter);
        }
    }

    public void setVisibilityForViews(boolean b, View... views){
        for(View anyView:views){
            if(b){
                anyView.setVisibility(View.VISIBLE);
            }else{
                anyView.setVisibility(View.GONE);
            }
        }
    }


    public void showToastMessage(final Integer message){
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
//    public void showAlert(final Integer title, final Integer message, final Integer neutralButton) { // title, message, positiveButton, neutralButton, negativeButton
    public void showAlert(final Integer ...alertComponents) { // title, message, positiveButton, neutralButton, negativeButton
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle(alertComponents[0])
                        .setMessage(alertComponents[1])
                        .setCancelable(false)
                        .setNeutralButton(alertComponents[2], new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //nothing
                            }})
                        .setPositiveButton(alertComponents[3], new DialogInterface.OnClickListener(){
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //nothing
                            }})
                        .setNegativeButton(alertComponents[4], new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //nothing
                            }
                        }).show();
                }
            }
        });
    }


    public void showLoadingDialog(Integer title, Integer message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setTitle(getString(title));
            progressDialog.setMessage(getString(message));
        }
        progressDialog.show();
    }

    public void dismissLoadingDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
