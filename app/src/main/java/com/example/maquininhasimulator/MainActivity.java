package com.example.maquininhasimulator;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static final String QRCODE_STORAGE = "QRCODE_STORAGE";
    public static final String LAST_READ_QRCODE = "LAST_READ_QRCODE";
    public static final String DEFAULT_QRCODE = "00020126750014BR.GOV.BCB.PIX013637109bf1-8b06-43c7-bdb2-95768909fe2d0213Doacao Unicef5204000053039865802BR5925FUNDO DAS NACOES UNIDAS P6009SAO.PAULO62070503***63047B1B";
    private NfcAdapter nfcAdapter;
    private TextView localizarDispositivoView;
    private TextView enviarSelectView;
    private TextView enviarUpdateView;
    private TextView statusNFC;
    private TextView qrCodeAtual;

    private String qrCode;
    private ImageView qrCodeImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
        resetUI();
        initNFC();
    }

    private void resetUI() {
        localizarDispositivoView = findViewById(R.id.localizar_dispositivo);
        localizarDispositivoView.setText(getText(R.string.localizar_dispositvo));

        enviarSelectView = findViewById(R.id.enviar_select);
        enviarSelectView.setText(getText(R.string.enviar_select));

        enviarUpdateView = findViewById(R.id.enviar_update);
        enviarUpdateView.setText(getText(R.string.enviar_update));

        statusNFC = findViewById(R.id.status_nfc);
        statusNFC.setText(getText(R.string.status_nfc));

        qrCodeImage = findViewById(R.id.qrcode_image);
        qrCodeImage.setImageResource(android.R.drawable.ic_dialog_alert);

        qrCodeAtual = findViewById(R.id.qrcode_atual);
        qrCodeAtual.setText("-");
        configureQRCode(getQRcode());

        Button resetButton = findViewById(R.id.reset);
        resetButton.setOnClickListener(view -> {
            @SuppressLint("UnsafeIntentLaunch") Intent intent = getIntent();
            finish();
            startActivity(intent);
        });

        Button capturarQRCode = findViewById(R.id.capturar_qr_code);
        capturarQRCode.setOnClickListener(view -> {
            IntentIntegrator intentIntegrator = new IntentIntegrator(MainActivity.this);
            intentIntegrator.setPrompt("Ler QRCode");
            intentIntegrator.setOrientationLocked(false);
            intentIntegrator.initiateScan(Set.of(IntentIntegrator.QR_CODE));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        NfcAdapter.ReaderCallback readerCallback = tag -> {
            executeOnMainThread(()-> localizarDispositivoView.setText(String.format("%s%s", localizarDispositivoView.getText(), getString(R.string.ok))));
            handleTag(tag);
        };

        int flags = NfcAdapter.FLAG_READER_NFC_A |
                NfcAdapter.FLAG_READER_NFC_B |
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK;
        nfcAdapter.enableReaderMode(this, readerCallback, flags, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcAdapter.disableReaderMode(this);
    }

    private void handleTag(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep != null) {
            try {
                isoDep.connect();

                byte[] selectAidCommand = createSelectCommand();
                byte[] responseApdu = isoDep.transceive(selectAidCommand);

                Log.d("APDU", "Resposta SELECT: "+byteToHex(responseApdu));
                if (!validateApduResponse(responseApdu)) {
                    showToastOnMainThread("Erro no comando SELECT, operação interrompida.");
                    executeOnMainThread(()->enviarSelectView.setText(String.format("%s%s", enviarSelectView.getText(), getString(R.string.nok))));
                    return;
                }

                executeOnMainThread(()->enviarSelectView.setText(String.format("%s%s", enviarSelectView.getText(), getString(R.string.ok))));
                configureQRCode(getQRcode());

                String assinaturaEletronica = UUID.randomUUID().toString()+"-"+UUID.randomUUID().toString()+"-"+UUID.randomUUID().toString()+"-"+UUID.randomUUID().toString();

                NdefRecord ndefRecord = NdefRecord.createUri("pix://maquininhadepagto?qr=" + this.qrCode + "&sig=" + assinaturaEletronica);
                byte[] updateResponse = isoDep.transceive(createUpdateApdu(ndefRecord.toByteArray()));
                Log.d("APDU", "Resposta do comando UPDATE: " + byteToHex(updateResponse));
                if (!validateApduResponse(updateResponse)) {
                    showToastOnMainThread("Erro no comando UPDATE, operação interrompida.");
                    Log.d("APDU", "Erro no comando UPDATE.");
                    executeOnMainThread(()->enviarUpdateView.setText(String.format("%s%s", enviarUpdateView.getText(), getString(R.string.nok))));
                    return;
                }

                executeOnMainThread(()->enviarUpdateView.setText(String.format("%s%s", enviarUpdateView.getText(), getString(R.string.ok))));

            } catch (IOException e) {
                Toast.makeText(this, "Erro:"+ e.getMessage(), Toast.LENGTH_SHORT).show();

            } finally {
                try {
                    isoDep.close();
                } catch (IOException e) {
                    Toast.makeText(this, "Erro ao fechar:"+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void configureQRCode(String qrCode) {
        this.qrCode = qrCode;

        Log.d("APDU", "QRCode:"+this.qrCode);
        executeOnMainThread(()->{
            qrCodeAtual.setText(this.qrCode);
            this.qrCodeImage.setImageBitmap(this.generateQRCode(this.qrCode));
            ;
        });
        SharedPreferences.Editor qrcodeStorage = this.getSharedPreferences(QRCODE_STORAGE, Context.MODE_PRIVATE).edit();
        qrcodeStorage.putString(LAST_READ_QRCODE, qrCode);
        qrcodeStorage.apply();
    }

    private String getQRcode() {
        SharedPreferences qrcodeStorage = this.getSharedPreferences(QRCODE_STORAGE, Context.MODE_PRIVATE);
        return qrcodeStorage.getString(LAST_READ_QRCODE, DEFAULT_QRCODE);
    }

    private boolean validateApduResponse(byte[] responseApdu) {
        return responseApdu.length >= 2 &&
                responseApdu[responseApdu.length - 2] == (byte) 0x90 &&
                responseApdu[responseApdu.length - 1] == (byte) 0x00;
    }

    private static byte [] createSelectCommand() {
        return new byte[]{
                (byte) 0x00, // CLA
                (byte) 0xA4, // INS (SELECT)
                (byte) 0x04, // P1 (Selection by name)
                (byte) 0x00, // P2 (First or only occurrence)
                (byte) 0x07, // Lc (Length of the AID)
                (byte) 0xF0, (byte) 0x39, (byte) 0x41, (byte) 0x48, (byte) 0x14, (byte) 0x81, (byte) 0x00, // AID F0394148148100
                (byte) 0x00
        };
    }

    private byte[] createUpdateApdu(byte[] urlBytes) {
        byte[] command = new byte[urlBytes.length + 7];

        command[0] = (byte) 0x00; // CLA
        command[1] = (byte) 0xD6; // INS (UPDATE)
        command[2] = (byte) 0x00; // P1 (Parameter 1)
        command[3] = (byte) 0x00; // P2 (Parameter 2)
        command[4] = (byte) 0x00;

        byte[] size = getDataSize(urlBytes);
        command[5] = size[0];
        command[6] = size[1];

        System.arraycopy(urlBytes, 0, command, 7, urlBytes.length);
        return command;
    }

    private byte[] getDataSize(byte[] urlBytes) {
        int length = urlBytes.length;
        return new byte[]{(byte) (length>>8 & 0xFF), (byte) (length & 0xFF)};
    }

    @SuppressLint("SetTextI18n")
    private void initNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC não encontrado", Toast.LENGTH_SHORT).show();
            statusNFC.setText(statusNFC.getText() + "Não encontrado");
            return;
        } else {
            statusNFC.setText(statusNFC.getText() + "Encontrado|");
        }

        if(!getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)){
            Toast.makeText(this, "Sistem não possui NFC", Toast.LENGTH_SHORT).show();
            statusNFC.setText(statusNFC.getText() + "Não possui NFC");
            return;
        }else {
            statusNFC.setText(statusNFC.getText() + "possui NFC|");
        }


        if(!nfcAdapter.isEnabled()){
            Toast.makeText(this, "NFC não habilitado", Toast.LENGTH_SHORT).show();
            statusNFC.setText(statusNFC.getText() + "Não habilitado");
            return;
        } else {
            statusNFC.setText(statusNFC.getText() + "Habilitado|");
        }

        if(getApplicationContext().checkPermission(
                "android.permission.NFC", android.os.Process.myPid(), android.os.Process.myUid())
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "NFC não permitido", Toast.LENGTH_SHORT).show();
            statusNFC.setText(statusNFC.getText() + "Não permitido");
        } else {
            statusNFC.setText(statusNFC.getText() + "OK|");
        }
    }

    private void init() {
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private void showToastOnMainThread(String message) {
        executeOnMainThread(()-> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void executeOnMainThread(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    public String byteToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X", b);
            hexString.append(hex).append(" ");
        }
        return hexString.toString().trim(); // Remove o espaço extra no final
    }

    private Bitmap generateQRCode(String text) {
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        try {
            return barcodeEncoder.encodeBitmap(text, BarcodeFormat.QR_CODE, 300, 300);
        }
        catch (WriterException e) {
            Toast.makeText(this, "Erro ao gerar QRCode:"+ e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (intentResult != null) {
            String contents = intentResult.getContents();
            configureQRCode(contents);
        }
    }
}