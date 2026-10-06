package com.nico.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.*;
import android.webkit.*;
import android.widget.*;

import java.net.*;
import java.io.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQ_AUDIO = 77;
    private static final String PREFS = "nico_mobile";

    private FrameLayout shell;
    private LinearLayout content;
    private NicoFaceView face;
    private TextView status;
    private TextView transcript;
    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private boolean openRemoteWhenOnline = false;

    private int cyan() { return Color.rgb(53, 216, 255); }
    private int violet() { return Color.rgb(140, 124, 255); }
    private int text() { return Color.rgb(224, 248, 255); }
    private int muted() { return Color.rgb(105, 142, 158); }
    private int green() { return Color.rgb(63, 235, 156); }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + .5f);
    }

    private String pcIp() { return prefs.getString("pc_ip", ""); }
    private String mac() { return prefs.getString("mac", ""); }
    private String broadcast() { return prefs.getString("broadcast", "192.168.1.255"); }
    private int wolPort() { return prefs.getInt("wol_port", 9); }
    private String mobileVoiceName() { return prefs.getString("mobile_voice_name", ""); }
    private String bridgeCode() { return prefs.getString("bridge_code", ""); }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildHome();
        initTts();
        initSpeech();

        if (pcIp().isEmpty() || mac().isEmpty()) {
            new Handler(Looper.getMainLooper()).postDelayed(this::showSettings, 500);
        } else {
            checkPc(false);
        }
    }

    private GradientDrawable rounded(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable gradient(int c1, int c2, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView label(String value, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setFontFeatureSettings("kern");
        return t;
    }

    private Button actionButton(String title, String subtitle) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(title + "\n" + subtitle);
        b.setTextSize(11);
        b.setTextColor(text());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        b.setBackground(gradient(
                Color.rgb(8, 19, 29),
                Color.rgb(5, 11, 18),
                Color.rgb(24, 68, 88),
                18));
        return b;
    }

    private void buildHome() {
        shell = new FrameLayout(this);
        shell.setBackgroundColor(Color.rgb(2, 5, 10));
        setContentView(shell);

        BackgroundView bg = new BackgroundView(this);
        shell.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shell.addView(scroller, new FrameLayout.LayoutParams(-1, -1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(20));
        scroller.addView(content, new ScrollView.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandCol = new LinearLayout(this);
        brandCol.setOrientation(LinearLayout.VERTICAL);

        TextView brand = label("NICO", 27, cyan());
        brand.setGravity(Gravity.START);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        brand.setLetterSpacing(.08f);
        brandCol.addView(brand);

        TextView sub = label("MOBILE INTELLIGENCE", 9, muted());
        sub.setGravity(Gravity.START);
        sub.setLetterSpacing(.18f);
        brandCol.addView(sub);

        top.addView(brandCol, new LinearLayout.LayoutParams(0, dp(60), 1));

        TextView live = label("●  LINK", 10, green());
        live.setPadding(dp(12), dp(8), dp(12), dp(8));
        live.setBackground(rounded(Color.rgb(5, 26, 24), Color.rgb(24, 80, 69), 14));
        top.addView(live, new LinearLayout.LayoutParams(dp(78), dp(38)));

        Space gap = new Space(this);
        top.addView(gap, new LinearLayout.LayoutParams(dp(8), 1));

        Button settings = new Button(this);
        settings.setText("⚙");
        settings.setTextSize(19);
        settings.setTextColor(text());
        settings.setBackground(rounded(Color.rgb(7, 16, 24), Color.rgb(24, 68, 88), 14));
        settings.setOnClickListener(v -> showSettings());
        top.addView(settings, new LinearLayout.LayoutParams(dp(46), dp(46)));

        content.addView(top, new LinearLayout.LayoutParams(-1, dp(64)));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(10), dp(8), dp(10), dp(10));
        hero.setBackground(gradient(
                Color.argb(222, 7, 18, 28),
                Color.argb(238, 3, 8, 14),
                Color.rgb(24, 75, 98),
                26));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, dp(410));
        heroLp.setMargins(0, dp(14), 0, dp(12));
        content.addView(hero, heroLp);

        TextView heroTag = label("NEURAL VOICE // WINDOWS LINK", 9, muted());
        heroTag.setLetterSpacing(.12f);
        hero.addView(heroTag, new LinearLayout.LayoutParams(-1, dp(30)));

        face = new NicoFaceView(this);
        hero.addView(face, new LinearLayout.LayoutParams(-1, 0, 1));

        status = label("CONFIGURE SEU PC", 11, cyan());
        status.setLetterSpacing(.08f);
        status.setPadding(dp(12), dp(9), dp(12), dp(9));
        status.setBackground(rounded(Color.rgb(5, 22, 31), Color.rgb(26, 74, 96), 14));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, dp(42));
        stLp.setMargins(dp(8), 0, dp(8), dp(4));
        hero.addView(status, stLp);

        transcript = label("Toque no microfone e fale naturalmente com o NICO.", 12, muted());
        transcript.setPadding(dp(6), dp(6), dp(6), dp(10));
        content.addView(transcript, new LinearLayout.LayoutParams(-1, dp(52)));

        Button mic = new Button(this);
        mic.setAllCaps(false);
        mic.setText("◉   FALAR COM NICO");
        mic.setTextSize(14);
        mic.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        mic.setTextColor(Color.rgb(225, 251, 255));
        mic.setBackground(gradient(
                Color.rgb(8, 75, 101),
                Color.rgb(21, 42, 88),
                cyan(),
                19));
        mic.setOnClickListener(v -> startListening());
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(-1, dp(58));
        micLp.setMargins(0, 0, 0, dp(10));
        content.addView(mic, micLp);

        Button typeCommand = new Button(this);
        typeCommand.setAllCaps(false);
        typeCommand.setText("⌨   DIGITAR COMANDO PARA O PC");
        typeCommand.setTextSize(12);
        typeCommand.setTextColor(text());
        typeCommand.setBackground(rounded(Color.rgb(7, 16, 24), Color.rgb(32, 91, 116), 17));
        typeCommand.setOnClickListener(v -> showCommandDialog());
        LinearLayout.LayoutParams cmdLp = new LinearLayout.LayoutParams(-1, dp(52));
        cmdLp.setMargins(0, 0, 0, dp(10));
        content.addView(typeCommand, cmdLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setRowCount(3);
        grid.setUseDefaultMargins(false);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);

        Button wake = actionButton("⚡  LIGAR PC", "Wake-on-LAN");
        Button stat = actionButton("●  STATUS", "Verificar conexão");
        Button remote = actionButton("▣  REMOTO", "Abrir NICO do PC");
        Button setup = actionButton("⚙  CONFIGURAR", "IP, MAC e rede");
        Button shutdown = actionButton("⏻  DESLIGAR PC", "Confirmação no celular");
        Button restart = actionButton("↻  REINICIAR PC", "Confirmação no celular");

        GridLayout.LayoutParams p1 = cell(0,0);
        GridLayout.LayoutParams p2 = cell(0,1);
        GridLayout.LayoutParams p3 = cell(1,0);
        GridLayout.LayoutParams p4 = cell(1,1);
        GridLayout.LayoutParams p5 = cell(2,0);
        GridLayout.LayoutParams p6 = cell(2,1);
        grid.addView(wake, p1);
        grid.addView(stat, p2);
        grid.addView(remote, p3);
        grid.addView(setup, p4);
        grid.addView(shutdown, p5);
        grid.addView(restart, p6);

        wake.setOnClickListener(v -> wakePc(false));
        stat.setOnClickListener(v -> checkPc(true));
        remote.setOnClickListener(v -> openDashboard());
        setup.setOnClickListener(v -> showSettings());
        shutdown.setOnClickListener(v -> confirmPowerAction("Desligar computador", "shutdown"));
        restart.setOnClickListener(v -> confirmPowerAction("Reiniciar computador", "restart"));

        content.addView(grid, new LinearLayout.LayoutParams(-1, dp(272)));

        TextView footer = label(
                "NICO MOBILE  •  BRIDGE 8765  •  V1.2",
                8, Color.rgb(68, 98, 112));
        footer.setLetterSpacing(.08f);
        footer.setPadding(0, dp(12), 0, 0);
        content.addView(footer);
    }

    private GridLayout.LayoutParams cell(int row, int col) {
        GridLayout.LayoutParams p = new GridLayout.LayoutParams(
                GridLayout.spec(row, 1f),
                GridLayout.spec(col, 1f));
        p.width = 0;
        p.height = dp(84);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        p.setGravity(Gravity.FILL);
        return p;
    }

    private void initTts() {
        tts = new TextToSpeech(this, code -> {
            if (code == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
                tts.setSpeechRate(1.08f);
                tts.setPitch(.92f);
                applySavedMobileVoice();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> {
                            face.setSpeaking(true);
                            face.setMode("FALANDO");
                        });
                    }
                    @Override public void onDone(String id) {
                        runOnUiThread(() -> {
                            face.setSpeaking(false);
                            face.setMode("ONLINE");
                        });
                    }
                    @Override public void onError(String id) {
                        runOnUiThread(() -> face.setSpeaking(false));
                    }
                });
            }
        });
    }

    private void say(String value) {
        transcript.setText(value);
        if (tts != null) {
            Bundle b = new Bundle();
            tts.speak(value, TextToSpeech.QUEUE_FLUSH, b, "nico_" + System.currentTimeMillis());
        }
    }

    private void initSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                face.setListening(true);
                face.setMode("OUVINDO");
                status.setText("OUVINDO…");
                status.setTextColor(cyan());
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {
                face.setMicLevel(Math.max(0f, Math.min(1f, rmsdB / 12f)));
            }
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                face.setListening(false);
                face.setMode("PROCESSANDO");
                status.setText("PROCESSANDO…");
            }
            @Override public void onError(int error) {
                face.setListening(false);
                face.setMode("ONLINE");
                status.setText("TOQUE NO MICROFONE PARA TENTAR NOVAMENTE");
            }
            @Override public void onResults(Bundle results) {
                face.setListening(false);
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String q = (list == null || list.isEmpty()) ? "" : list.get(0);
                transcript.setText("Você: " + q);
                handleVoice(q);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list != null && !list.isEmpty()) transcript.setText(list.get(0));
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListening() {
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        if (recognizer == null) {
            say("O reconhecimento de voz não está disponível neste celular.");
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(i);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }

    private String norm(String value) {
        String n = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean any(String q, String... values) {
        for (String v : values) if (q.contains(v)) return true;
        return false;
    }

    private void handleVoice(String raw) {
        String q = norm(raw);
        boolean power = any(q, "liga", "ligar", "acorda", "acordar", "inicia", "iniciar")
                && any(q, "pc", "computador", "nico");
        boolean statusQuery = any(q, "status", "esta ligado", "ta ligado", "online");
        boolean remote = any(q, "painel", "remoto", "controle", "conecta", "conectar");
        boolean shutdownVoice = any(q, "desliga", "desligar", "desligue") && any(q, "pc", "computador");
        boolean restartVoice = any(q, "reinicia", "reiniciar", "reinicie") && any(q, "pc", "computador");

        if (shutdownVoice) {
            confirmPowerAction("Desligar computador", "shutdown");
            return;
        }
        if (restartVoice) {
            confirmPowerAction("Reiniciar computador", "restart");
            return;
        }

        if (power) {
            wakePc(remote);
            return;
        }
        if (statusQuery) {
            checkPc(true);
            return;
        }
        if (remote) {
            openDashboard();
            return;
        }

        if (pcOnlineQuick()) {
            relayCommandToPc(raw);
        } else {
            say("Esse comando precisa do NICO do computador online. Ligue o PC e tente novamente.");
        }
    }

    private byte[] parseMac(String raw) {
        String s = raw.replace("-", "").replace(":", "").trim();
        if (s.length() != 12) throw new IllegalArgumentException("MAC inválido");
        byte[] out = new byte[6];
        for (int i = 0; i < 6; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private void wakePc(boolean openRemote) {
        if (mac().isEmpty()) {
            say("Configure o endereço MAC do computador primeiro.");
            showSettings();
            return;
        }
        openRemoteWhenOnline = openRemote;
        face.setMode("ENVIANDO WOL");
        status.setText("ENVIANDO SINAL PARA O PC…");
        new Thread(() -> {
            try {
                byte[] hw = parseMac(mac());
                byte[] packet = new byte[6 + 16 * hw.length];
                Arrays.fill(packet, 0, 6, (byte) 0xFF);
                for (int i = 6; i < packet.length; i += hw.length) {
                    System.arraycopy(hw, 0, packet, i, hw.length);
                }

                DatagramSocket socket = new DatagramSocket();
                socket.setBroadcast(true);
                InetAddress target = InetAddress.getByName(broadcast());
                DatagramPacket dp = new DatagramPacket(packet, packet.length, target, wolPort());
                for (int i = 0; i < 4; i++) {
                    socket.send(dp);
                    Thread.sleep(120);
                }
                socket.close();

                runOnUiThread(() -> {
                    say("Sinal enviado. Estou esperando o computador ligar.");
                    status.setText("PC INICIANDO…");
                    face.setMode("INICIANDO");
                });
                pollOnline();
            } catch (Exception e) {
                runOnUiThread(() -> {
                    face.setMode("ERRO");
                    status.setText("ERRO NO WAKE-ON-LAN");
                    say("Não consegui enviar o sinal. Confira o MAC e o broadcast nas configurações.");
                });
            }
        }).start();
    }

    private boolean portOpen(String host, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean pcOnlineQuick() {
        String ip = pcIp();
        if (ip.isEmpty()) return false;
        return portOpen(ip, 8765, 2500);
    }

    private boolean pcOnline() {
        if (pcOnlineQuick()) return true;
        try {
            if (!pcIp().isEmpty() && InetAddress.getByName(pcIp()).isReachable(700)) return true;
        } catch (Exception ignored) {}

        // DHCP can change the PC IP. If the saved IP stopped responding,
        // scan the same /24 subnet for the NICO dashboard and update automatically.
        String found = discoverPcOnSubnet();
        if (found != null && !found.isEmpty()) {
            prefs.edit().putString("pc_ip", found).apply();
            return true;
        }
        return false;
    }

    private String discoverPcOnSubnet() {
        String ip = pcIp();
        if (ip == null || ip.isEmpty()) return null;

        int lastDot = ip.lastIndexOf('.');
        if (lastDot <= 0) return null;
        String prefix = ip.substring(0, lastDot + 1);

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CompletionService<String> cs = new ExecutorCompletionService<>(pool);
        int jobs = 0;

        try {
            for (int i = 1; i <= 254; i++) {
                final String host = prefix + i;
                if (host.equals(ip)) continue;
                jobs++;
                cs.submit(() -> {
                    if (portOpen(host, 8765, 700)) return host;
                    return null;
                });
            }

            for (int i = 0; i < jobs; i++) {
                try {
                    Future<String> f = cs.poll(350, TimeUnit.MILLISECONDS);
                    if (f == null) continue;
                    String found = f.get();
                    if (found != null) {
                        pool.shutdownNow();
                        return found;
                    }
                } catch (Exception ignored) {}
            }
        } finally {
            pool.shutdownNow();
        }
        return null;
    }

    private void checkPc(boolean speak) {
        if (pcIp().isEmpty()) {
            showSettings();
            return;
        }
        status.setText("VERIFICANDO PC  •  " + pcIp());
        face.setMode("VERIFICANDO");
        new Thread(() -> {
            boolean online = pcOnline();
            runOnUiThread(() -> {
                if (online) {
                    status.setText("●  NICO DO PC ONLINE  •  " + pcIp());
                    status.setTextColor(green());
                    face.setMode("ONLINE");
                    if (speak) say("Seu computador está online.");
                } else {
                    status.setText("○  NICO DO PC NÃO RESPONDEU");
                    status.setTextColor(muted());
                    face.setMode("OFFLINE");
                    if (speak) say("O NICO do PC não respondeu na ponte mobile. Confira o IP, o Wi-Fi e se a porta 8765 está ativa.");
                }
            });
        }).start();
    }

    private void pollOnline() {
        if (!polling.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(1800);
                    if (pcOnline()) {
                        runOnUiThread(() -> {
                            status.setText("●  NICO DO PC ONLINE");
                            status.setTextColor(green());
                            face.setMode("ONLINE");
                            say("Pronto. Seu computador está online.");
                            if (openRemoteWhenOnline) {
                                openRemoteWhenOnline = false;
                                new Handler(Looper.getMainLooper()).postDelayed(this::openDashboard, 1000);
                            }
                        });
                        return;
                    }
                }
                runOnUiThread(() -> {
                    status.setText("SEM RESPOSTA DO PC");
                    face.setMode("OFFLINE");
                    say("O computador ainda não respondeu. Confira a rede e o Wake-on-LAN.");
                });
            } catch (InterruptedException ignored) {
            } finally {
                polling.set(false);
            }
        }).start();
    }

    private void openDashboard() {
        final String ip = pcIp();
        if (ip.isEmpty()) {
            showSettings();
            return;
        }

        FrameLayout remoteShell = new FrameLayout(this);
        remoteShell.setBackgroundColor(Color.rgb(2, 5, 10));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        remoteShell.addView(wrap, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.rgb(4, 10, 16));

        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(cyan());
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> buildHome());
        bar.addView(back, new LinearLayout.LayoutParams(dp(54), dp(48)));

        TextView title = label("NICO  //  REMOTE", 13, text());
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView secure = label("LOCAL", 9, green());
        secure.setPadding(dp(10), dp(5), dp(10), dp(5));
        secure.setBackground(rounded(Color.rgb(5, 26, 24), Color.rgb(24, 80, 69), 12));
        bar.addView(secure, new LinearLayout.LayoutParams(dp(68), dp(34)));
        wrap.addView(bar, new LinearLayout.LayoutParams(-1, dp(64)));

        WebView web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        final boolean[] triedHttp = {false};

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (Build.VERSION.SDK_INT >= 23 &&
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                        request.deny();
                        return;
                    }
                    List<String> allowed = new ArrayList<>();
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) allowed.add(r);
                    }
                    if (allowed.isEmpty()) request.deny();
                    else request.grant(allowed.toArray(new String[0]));
                });
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                           android.net.http.SslError error) {
                String host = "";
                try { host = Uri.parse(error.getUrl()).getHost(); } catch (Exception ignored) {}
                if (!ip.equals(host)) {
                    handler.cancel();
                    return;
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Conexão local do NICO")
                        .setMessage("O NICO do PC usa um certificado criado localmente. Continue apenas na sua própria rede.")
                        .setPositiveButton("Conectar", (d, w) -> handler.proceed())
                        .setNegativeButton("Cancelar", (d, w) -> handler.cancel())
                        .show();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !triedHttp[0]) {
                    triedHttp[0] = true;
                    view.loadUrl("http://" + ip + ":8000/");
                }
            }
        });

        wrap.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(remoteShell);
        web.loadUrl("https://" + ip + ":8000/");
    }

    private void showCommandDialog() {
        if (pcIp().isEmpty()) {
            showSettings();
            return;
        }
        final EditText input = field("Ex.: Nico, abra o Chrome e entre no YouTube", "");
        input.setSingleLine(false);
        input.setMinLines(3);

        new AlertDialog.Builder(this)
                .setTitle("Comando para o NICO do PC")
                .setMessage("O comando será interpretado pelo mesmo NICO que roda no computador.")
                .setView(input)
                .setPositiveButton("Enviar", (d, w) -> {
                    String cmd = input.getText().toString().trim();
                    if (!cmd.isEmpty()) relayCommandToPc(cmd);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String bridgePost(String endpoint, JSONObject body) throws Exception {
        URL url = new URL("http://" + pcIp() + ":8765" + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(3500);
        conn.setReadTimeout(8000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        byte[] payload = body.toString().getBytes("UTF-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int statusCode = conn.getResponseCode();
        InputStream in = statusCode >= 200 && statusCode < 300
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder out = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line);
            }
        }
        conn.disconnect();
        return statusCode + "|" + out;
    }

    private void relayCommandToPc(String command) {
        final String clean = command == null ? "" : command.trim();
        if (pcIp().isEmpty() || clean.isEmpty()) return;

        if (bridgeCode().isEmpty()) {
            say("Configure o Código do PC no NICO Mobile primeiro.");
            showSettings();
            return;
        }

        status.setText("ENVIANDO COMANDO AO PC…");
        face.setMode("COMANDANDO PC");
        transcript.setText("Você → PC: " + clean);

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("code", bridgeCode());
                body.put("text", clean);

                String raw = bridgePost("/nico/command", body);
                int sep = raw.indexOf('|');
                int http = sep > 0 ? Integer.parseInt(raw.substring(0, sep)) : 0;
                String json = sep >= 0 ? raw.substring(sep + 1) : "{}";
                JSONObject resp = new JSONObject(json.isEmpty() ? "{}" : json);

                runOnUiThread(() -> {
                    if (http >= 200 && http < 300 && resp.optBoolean("ok", false)) {
                        status.setText("●  COMANDO ENVIADO AO NICO DO PC");
                        status.setTextColor(green());
                        face.setMode("PC EXECUTANDO");
                        say("Comando enviado para o NICO do computador.");
                    } else if ("invalid_code".equals(resp.optString("error"))) {
                        face.setMode("ERRO");
                        say("O Código do PC está errado. Abra as configurações e coloque o código mostrado pelo NICO no computador.");
                    } else {
                        face.setMode("ERRO");
                        say("Não consegui enviar o comando para o computador.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    face.setMode("ERRO");
                    status.setText("PC NÃO RESPONDEU NA PORTA 8765");
                    say("Não consegui conectar à ponte mobile do NICO no computador.");
                });
            }
        }).start();
    }

    public class CommandBridge {
        private final WebView web;
        CommandBridge(WebView web) { this.web = web; }

        private void cleanup() {
            try {
                ViewGroup parent = (ViewGroup) web.getParent();
                if (parent != null) parent.removeView(web);
                web.destroy();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void commandOk() {
            runOnUiThread(() -> {
                status.setText("●  COMANDO ENVIADO AO NICO DO PC");
                status.setTextColor(green());
                face.setMode("PC EXECUTANDO");
                say("Comando enviado para o NICO do computador.");
                cleanup();
            });
        }

        @JavascriptInterface
        public void commandError(String error) {
            runOnUiThread(() -> {
                face.setMode("ERRO");
                if ("not_paired".equals(error)) {
                    say("Primeiro abra Remoto e faça o pareamento com o NICO do computador. Depois os comandos funcionam direto pelo celular.");
                } else {
                    say("Não consegui enviar o comando ao computador: " + error);
                }
                cleanup();
            });
        }
    }

    private void applySavedMobileVoice() {
        if (tts == null) return;
        String wanted = mobileVoiceName();
        if (wanted.isEmpty()) return;
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            for (Voice v : voices) {
                if (wanted.equals(v.getName())) {
                    tts.setVoice(v);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private void showVoicePicker() {
        if (tts == null || tts.getVoices() == null) {
            say("As vozes ainda estão carregando. Tente novamente em alguns segundos.");
            return;
        }

        List<Voice> voices = new ArrayList<>();
        for (Voice v : tts.getVoices()) {
            Locale locale = v.getLocale();
            if (locale != null && "pt".equalsIgnoreCase(locale.getLanguage())) {
                voices.add(v);
            }
        }

        if (voices.isEmpty()) voices.addAll(tts.getVoices());
        Collections.sort(voices, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        String[] labels = new String[voices.size()];
        int selected = -1;
        String current = mobileVoiceName();

        for (int i = 0; i < voices.size(); i++) {
            Voice v = voices.get(i);
            Locale loc = v.getLocale();
            String lang = loc == null ? "" : loc.toLanguageTag();
            labels[i] = v.getName() + (lang.isEmpty() ? "" : "  •  " + lang)
                    + (v.isNetworkConnectionRequired() ? "  •  online" : "  •  local");
            if (v.getName().equals(current)) selected = i;
        }

        final List<Voice> pick = voices;
        new AlertDialog.Builder(this)
                .setTitle("Voz do NICO no celular")
                .setSingleChoiceItems(labels, selected, (d, which) -> {
                    Voice chosen = pick.get(which);
                    prefs.edit().putString("mobile_voice_name", chosen.getName()).apply();
                    tts.setVoice(chosen);
                    d.dismiss();
                    say("Essa será minha voz no celular.");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmPowerAction(String title, String action) {
        if (pcIp().isEmpty()) {
            showSettings();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title + "?")
                .setMessage(action.equals("shutdown")
                        ? "O computador será desligado. Confirme apenas se você realmente quer encerrar a sessão."
                        : "O computador será reiniciado. Confirme apenas se você realmente quer continuar.")
                .setPositiveButton(action.equals("shutdown") ? "Desligar" : "Reiniciar",
                        (d, w) -> sendBridgePower(action))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void sendBridgePower(String action) {
        if (bridgeCode().isEmpty()) {
            say("Configure o Código do PC primeiro.");
            showSettings();
            return;
        }

        status.setText(action.equals("shutdown") ? "DESLIGANDO PC…" : "REINICIANDO PC…");
        face.setMode("ENERGIA");

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("code", bridgeCode());
                body.put("action", action);
                body.put("confirmed", true);

                String raw = bridgePost("/nico/power", body);
                int sep = raw.indexOf('|');
                int http = sep > 0 ? Integer.parseInt(raw.substring(0, sep)) : 0;
                String json = sep >= 0 ? raw.substring(sep + 1) : "{}";
                JSONObject resp = new JSONObject(json.isEmpty() ? "{}" : json);

                runOnUiThread(() -> {
                    if (http >= 200 && http < 300 && resp.optBoolean("ok", false)) {
                        say(action.equals("shutdown")
                                ? "Comando enviado. O computador está desligando."
                                : "Comando enviado. O computador está reiniciando.");
                        status.setText("COMANDO DE ENERGIA ENVIADO");
                        face.setMode("ONLINE");
                    } else if ("invalid_code".equals(resp.optString("error"))) {
                        say("O Código do PC está errado.");
                        face.setMode("ERRO");
                    } else {
                        say("Não consegui executar a ação de energia.");
                        face.setMode("ERRO");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    say("Não consegui conectar ao NICO do computador.");
                    face.setMode("ERRO");
                });
            }
        }).start();
    }

    private void openDashboardForPower(String action) {
        final String ip = pcIp();
        status.setText("CONECTANDO AO NICO DO PC…");
        face.setMode("CONECTANDO");

        FrameLayout remoteShell = new FrameLayout(this);
        remoteShell.setBackgroundColor(Color.rgb(2, 5, 10));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        remoteShell.addView(wrap, new FrameLayout.LayoutParams(-1, -1));

        TextView waiting = label("NICO // AUTENTICANDO AÇÃO DE ENERGIA", 12, text());
        waiting.setPadding(dp(16), dp(14), dp(16), dp(14));
        waiting.setBackground(rounded(Color.rgb(7, 16, 24), Color.rgb(24, 68, 88), 14));
        wrap.addView(waiting, new LinearLayout.LayoutParams(-1, dp(58)));

        WebView web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.addJavascriptInterface(new PowerBridge(action), "NicoNative");

        final boolean[] sent = {false};
        final boolean[] triedHttp = {false};

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler,
                                           android.net.http.SslError error) {
                String host = "";
                try { host = Uri.parse(error.getUrl()).getHost(); } catch (Exception ignored) {}
                if (ip.equals(host)) handler.proceed(); else handler.cancel();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame() && !triedHttp[0]) {
                    triedHttp[0] = true;
                    view.loadUrl("http://" + ip + ":8000/");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (sent[0]) return;
                Uri u;
                try { u = Uri.parse(url); } catch (Exception e) { return; }
                String path = u.getPath();
                if (!"/".equals(path)) return;

                sent[0] = true;
                waiting.setText(action.equals("shutdown")
                        ? "ENVIANDO COMANDO: DESLIGAR"
                        : "ENVIANDO COMANDO: REINICIAR");

                String js =
                        "(async()=>{" +
                        "const t=sessionStorage.getItem('jarvis_token');" +
                        "if(!t){window.NicoNative.powerError('not_paired');return;}" +
                        "try{" +
                        "const r=await fetch('/api/mobile-power',{method:'POST',headers:{'Content-Type':'application/json','Authorization':'Bearer '+t},body:JSON.stringify({action:'" + action + "',confirm:'YES'})});" +
                        "const d=await r.json();" +
                        "if(r.ok&&d.ok)window.NicoNative.powerOk();else window.NicoNative.powerError(d.error||'failed');" +
                        "}catch(e){window.NicoNative.powerError(String(e));}" +
                        "})()";
                view.evaluateJavascript(js, null);
            }
        });

        wrap.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(remoteShell);
        web.loadUrl("https://" + ip + ":8000/");
    }

    public class PowerBridge {
        private final String action;
        PowerBridge(String action) { this.action = action; }

        @JavascriptInterface
        public void powerOk() {
            runOnUiThread(() -> {
                say(action.equals("shutdown")
                        ? "Comando confirmado. O computador está desligando."
                        : "Comando confirmado. O computador está reiniciando.");
                new Handler(Looper.getMainLooper()).postDelayed(() -> buildHome(), 1500);
            });
        }

        @JavascriptInterface
        public void powerError(String error) {
            runOnUiThread(() -> {
                if ("not_paired".equals(error)) {
                    say("Primeiro faça o pareamento com o NICO do computador nesta tela. Depois tente novamente.");
                } else {
                    say("Não consegui executar a ação de energia: " + error);
                }
            });
        }
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(text());
        e.setHintTextColor(Color.rgb(75, 105, 119));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(rounded(Color.rgb(7, 16, 25), Color.rgb(24, 68, 88), 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52));
        lp.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(lp);
        return e;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        TextView hint = label(
                "Esses dados ficam salvos somente neste celular.\nNenhuma chave Gemini é necessária no app.",
                11, muted());
        hint.setGravity(Gravity.START);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText ip = field("IP local do PC  •  ex. 192.168.1.100", pcIp());
        EditText hw = field("MAC da placa de rede  •  AA-BB-CC-DD-EE-FF", mac());
        EditText bc = field("Broadcast  •  ex. 192.168.1.255", broadcast());
        EditText pt = field("Porta Wake-on-LAN", String.valueOf(wolPort()));
        EditText code = field("Código do PC  •  ex. 7K4M9Q2H", bridgeCode());

        box.addView(ip);
        box.addView(hw);
        box.addView(bc);
        box.addView(pt);
        box.addView(code);

        Button voice = new Button(this);
        voice.setAllCaps(false);
        voice.setText("🎙  Escolher voz do NICO");
        voice.setTextColor(text());
        voice.setBackground(rounded(Color.rgb(7, 16, 25), Color.rgb(24, 68, 88), 12));
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(-1, dp(52));
        voiceLp.setMargins(0, dp(8), 0, dp(4));
        voice.setLayoutParams(voiceLp);
        voice.setOnClickListener(v -> showVoicePicker());
        box.addView(voice);

        new AlertDialog.Builder(this)
                .setTitle("Configurar NICO Mobile")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    int port = 9;
                    try { port = Integer.parseInt(pt.getText().toString().trim()); }
                    catch (Exception ignored) {}
                    prefs.edit()
                            .putString("pc_ip", ip.getText().toString().trim())
                            .putString("mac", hw.getText().toString().trim())
                            .putString("broadcast", bc.getText().toString().trim())
                            .putInt("wol_port", port)
                            .putString("bridge_code", code.getText().toString().trim().toUpperCase(Locale.ROOT))
                            .apply();
                    say("Configuração salva.");
                    checkPc(false);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        buildHome();
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    public static class BackgroundView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        public BackgroundView(Activity a) { super(a); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            p.setShader(new RadialGradient(w * .5f, h * .22f, w * .8f,
                    Color.argb(55, 0, 190, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
            p.setShader(new RadialGradient(w * .88f, h * .63f, w * .7f,
                    Color.argb(32, 121, 92, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p);
            p.setShader(null);
            p.setColor(Color.argb(20, 90, 200, 255));
            p.setStrokeWidth(1);
            for (int i = 0; i < 16; i++) {
                float y = h * i / 16f;
                c.drawLine(0, y, w, y, p);
            }
        }
    }

    public static class NicoFaceView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean speaking = false;
        private boolean listening = false;
        private float mic = 0f;
        private String mode = "ONLINE";
        private final long started = System.currentTimeMillis();

        private final int CYAN = Color.rgb(53, 216, 255);
        private final int LIGHT = Color.rgb(124, 235, 255);
        private final int VIOLET = Color.rgb(140, 124, 255);

        public NicoFaceView(Activity a) {
            super(a);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        public void setSpeaking(boolean value) { speaking = value; invalidate(); }
        public void setListening(boolean value) { listening = value; invalidate(); }
        public void setMicLevel(float value) { mic = value; invalidate(); }
        public void setMode(String value) { mode = value; invalidate(); }

        private float dp(float v) {
            return v * getResources().getDisplayMetrics().density;
        }

        private void stroke(int color, float width, int alpha) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(width));
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(color);
            p.setAlpha(alpha);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h * .48f;
            float r = Math.min(w, h) * .31f;
            float t = (System.currentTimeMillis() - started) / 1000f;

            p.setShader(new RadialGradient(cx, cy, r * 1.7f,
                    Color.argb(68, 0, 183, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, r * 1.65f, p);
            p.setShader(null);

            c.save();
            c.rotate(t * 13f, cx, cy);
            stroke(CYAN, 1.4f, 92);
            RectF outer = new RectF(cx-r*1.28f, cy-r*1.28f, cx+r*1.28f, cy+r*1.28f);
            for (int i = 0; i < 6; i++) c.drawArc(outer, i*60f+7f, 31f, false, p);
            c.restore();

            c.save();
            c.rotate(-t * 8f, cx, cy);
            stroke(VIOLET, 1.1f, 75);
            RectF inner = new RectF(cx-r*1.10f, cy-r*1.10f, cx+r*1.10f, cy+r*1.10f);
            for (int i = 0; i < 8; i++) c.drawArc(inner, i*45f+5f, 16f, false, p);
            c.restore();

            float pulse = listening
                    ? .5f + .5f * (float)Math.sin(t * 10f)
                    : .5f + .5f * (float)Math.sin(t * 2.2f);
            stroke(listening ? LIGHT : CYAN, 1.8f + pulse, listening ? 220 : 110);
            c.drawCircle(cx, cy, r * (1.39f + pulse * .025f), p);

            Path head = new Path();
            head.moveTo(cx-r*.60f, cy-r*.60f);
            head.lineTo(cx-r*.34f, cy-r*.88f);
            head.lineTo(cx+r*.34f, cy-r*.88f);
            head.lineTo(cx+r*.60f, cy-r*.60f);
            head.lineTo(cx+r*.64f, cy+r*.22f);
            head.lineTo(cx+r*.36f, cy+r*.76f);
            head.lineTo(cx, cy+r*.93f);
            head.lineTo(cx-r*.36f, cy+r*.76f);
            head.lineTo(cx-r*.64f, cy+r*.22f);
            head.close();

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(5, 14, 23));
            p.setAlpha(242);
            c.drawPath(head, p);
            stroke(CYAN, 2.0f, 215);
            c.drawPath(head, p);

            // temple plates
            stroke(Color.rgb(30, 91, 115), 1.2f, 170);
            c.drawLine(cx-r*.62f, cy-r*.20f, cx-r*.77f, cy, p);
            c.drawLine(cx+r*.62f, cy-r*.20f, cx+r*.77f, cy, p);

            // eyes
            float eye = .82f + .18f * (float)Math.sin(t * 2.5f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(LIGHT);
            p.setAlpha((int)(190 + eye * 60));
            c.drawRoundRect(new RectF(cx-r*.43f, cy-r*.16f, cx-r*.11f, cy-r*.07f), dp(5), dp(5), p);
            c.drawRoundRect(new RectF(cx+r*.11f, cy-r*.16f, cx+r*.43f, cy-r*.07f), dp(5), dp(5), p);

            stroke(CYAN, 1.8f, 150);
            c.drawLine(cx-r*.43f, cy-r*.29f, cx-r*.11f, cy-r*.23f, p);
            c.drawLine(cx+r*.43f, cy-r*.29f, cx+r*.11f, cy-r*.23f, p);

            // face bridge
            stroke(Color.rgb(35, 97, 119), 1.0f, 160);
            Path bridge = new Path();
            bridge.moveTo(cx-r*.08f, cy-r*.05f);
            bridge.lineTo(cx-r*.13f, cy+r*.18f);
            bridge.lineTo(cx, cy+r*.25f);
            bridge.lineTo(cx+r*.13f, cy+r*.18f);
            bridge.lineTo(cx+r*.08f, cy-r*.05f);
            c.drawPath(bridge, p);

            float mouth;
            if (speaking) {
                mouth = .20f + .80f * Math.abs(
                        (float)Math.sin(t * 13.0f) * .72f +
                        (float)Math.sin(t * 20.0f) * .28f);
            } else if (listening) {
                mouth = .10f + mic * .35f;
            } else {
                mouth = .08f;
            }

            float mw = r * .53f;
            float mh = r * (.055f + .18f * mouth);
            RectF m = new RectF(cx-mw/2, cy+r*.43f-mh/2, cx+mw/2, cy+r*.43f+mh/2);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(0, 28, 40));
            p.setAlpha(255);
            c.drawRoundRect(m, dp(8), dp(8), p);
            stroke(speaking ? LIGHT : CYAN, 1.5f, speaking ? 245 : 145);
            c.drawRoundRect(m, dp(8), dp(8), p);

            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            p.setTextSize(dp(9));
            p.setColor(CYAN);
            p.setAlpha(190);
            c.drawText("NICO  //  " + mode, cx, cy + r * 1.52f, p);

            postInvalidateDelayed(32);
        }
    }
}
