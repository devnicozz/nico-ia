# NICO AI

NICO é um assistente de IA para Windows e Android com voz, controle remoto, automações, criação de imagens, criação de sites e integração com o computador.

## Recursos

- Assistente nativo para Windows
- Aplicativo Android companheiro
- Robô/rosto animado com lip-sync
- Conversação por voz com Gemini Live
- Perfil de resposta rápida
- Controle de aplicativos, arquivos, navegador e área de trabalho
- Wake-on-LAN pelo celular
- Painel remoto
- WhatsApp Web
- Geração de imagens
- Criação de sites usando uma identidade visual mostrada na tela
- Atalhos e instalação normal no Windows

## Privacidade

Nenhuma Gemini API Key pessoal deve ser incluída no repositório ou em builds públicas.
Cada instalação configura a própria chave no primeiro uso.

## Builds

Os workflows do GitHub Actions geram as versões Android e Windows.

### Android

Abra **Actions → Build NICO Mobile APK** e baixe o artifact **NICO-Mobile-APK**.

### Windows

O instalador do Windows será distribuído como **NICO-Setup.exe** quando o workflow correspondente estiver finalizado.

## Avisos de terceiros

Os avisos legais de componentes de terceiros ficam em `THIRD_PARTY_NOTICES.txt` e nos arquivos de licença incluídos com a distribuição. Eles não precisam aparecer como créditos promocionais na interface principal do NICO.

## Uso comercial

Partes derivadas do Mark-LV estão sujeitas à licença CC BY-NC 4.0. Isso impede uso comercial dessas partes sem permissão adicional ou substituição por componentes com licença compatível.


## NICO PRO — atualização atual

A versão atual acrescenta:

- Android com botões **Ligar PC**, **Desligar PC**, **Reiniciar PC**, status, remoto e voz;
- desligar/reiniciar pelo celular exige confirmação humana no próprio celular e sessão remota autenticada;
- correção do workflow Android SDK;
- perfil de voz mais responsivo e menos propenso a ficar um turno atrasado;
- timeout de ferramentas para evitar que uma automação deixe o assistente preso;
- desativação do morning brief automático por padrão;
- geração de imagens com Gemini 3.1 Image e salvamento local;
- criação de sites a partir da identidade visual exibida na tela;
- troca de wallpaper por arquivo local ou URL direta;
- WhatsApp Web com verificação do contato;
- interface Windows NICO com HUD de rosto/lip-sync, paleta preto/ciano/violeta, tipografia moderna e painéis maiores;
- endpoint autenticado para ações de energia no Windows;
- workflow **Build NICO Windows Installer** para gerar `NICO-Setup.exe` e `NICO-Portable.zip`.

### Atualizar um NICO já instalado no PC

Baixe o repositório e execute:

`windows/APLICAR_NO_PC.bat`

A API key existente é mantida no arquivo local de configuração. Nunca envie esse arquivo ao GitHub.
