# NICO Mobile

Aplicativo Android companheiro do NICO para Windows.

## Visual
- interface escura premium em ciano/violeta;
- robô NICO animado;
- boca animada enquanto a voz TTS fala;
- olhos, anéis, pulso e estados de ouvindo/processando/online;
- painel remoto embutido em WebView.

## Funções
- Wake-on-LAN para ligar o PC na mesma rede Wi-Fi;
- reconhecimento de voz em português;
- status do NICO/PC;
- conexão com o Remote Dashboard do NICO no Windows;
- microfone liberado para o dashboard remoto;
- configuração local de IP, MAC, broadcast e porta WOL.

Nenhum IP, MAC ou chave Gemini pessoal é armazenado no repositório. Cada instalação configura o próprio PC no primeiro uso.

## Gerar APK
O workflow `.github/workflows/build-android-apk.yml` gera automaticamente o APK.

No GitHub:
1. abra **Actions**;
2. escolha **Build NICO Mobile APK**;
3. abra a execução concluída;
4. baixe o artifact **NICO-Mobile-APK**.

O arquivo dentro do artifact é `NICO-Mobile.apk`.
