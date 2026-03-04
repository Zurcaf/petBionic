# 🐾 IoT Smart Canine Prosthesis

Projeto desenvolvido no âmbito da disciplina de **Redes Móveis e Internet das Coisas**.

## 📌 Descrição

Este projeto consiste no desenvolvimento de uma **prótese canina instrumentada com sensores externos ao animal**, integrada numa arquitetura IoT com comunicação **BLE** e **Wi-Fi**.

O sistema permite:

- Recolher dados de carga exercida no solo
- Medir orientação e ângulo da prótese
- Armazenar dados localmente (SD Card)
- Comunicar via BLE com aplicação Android (GUI)
- Sincronizar dados via Wi-Fi para base de dados remota utilizando MQTT

O sistema segue um modelo **Store-and-Forward**, onde os dados são guardados localmente e enviados posteriormente quando existir ligação Wi-Fi.

---

# 🏗 Arquitetura do Sistema

## 📟 Edge Device (Prótese)

Hardware principal:

- Microcontrolador: Seeed Studio XIAO ESP32-C3
- Sensor inercial: MPU-9250
- Sensor de carga
- Módulo SD Card

Funções:

- Aquisição de dados
- Processamento básico
- Armazenamento local
- Comunicação BLE (controlo)
- Comunicação Wi-Fi (sincronização)

---

## 📱 Aplicação Android

Responsável por:

- Conectar via BLE
- Enviar comandos (Start/Stop testes)
- Configurar parâmetros
- Configurar rede Wi-Fi
- Visualizar estado do sistema
- Interface gráfica (GUI)

A aplicação não recebe dados continuamente — apenas envia comandos e consulta estado.

---

## 🌐 Comunicação Wi-Fi

O ESP32 liga-se à rede Wi-Fi configurada e envia os dados armazenados para um broker MQTT.

### Protocolo Utilizado:
- MQTT

### Modelo:
- ESP32 → MQTT Client (Publisher)
- Broker → Recebe dados
- Backend → Armazena em base de dados

---

# 🔄 Fluxo de Funcionamento

### Durante o dia:
Sensores → ESP32 → SD Card

### Quando houver Wi-Fi disponível:
ESP32 → Wi-Fi → MQTT Broker → Base de Dados

### Comunicação BLE:
App Android ↔ ESP32  
(Comandos e configuração apenas)

---

# 📡 Comunicações

## BLE

- ESP32 atua como BLE Peripheral
- Android atua como BLE Central
- Implementação via GATT (Custom Service)

Funções BLE:
- Start/Stop aquisição
- Configuração de parâmetros
- Configuração de Wi-Fi
- Consulta de estado (bateria, memória)

---

## Wi-Fi + MQTT

- Envio em batch
- QoS configurável
- Reconexão automática
- Modelo assíncrono

Exemplo de tópicos:

prostese/ID01/dados  
prostese/ID01/status  
prostese/ID01/sync  

---

# 🧠 Estrutura do Firmware (ESP32)
/src
├── main.cpp
├── SensorManager
├── StorageManager
├── BLEManager
├── WiFiManager
├── MQTTManager
├── SyncManager
└── ConfigManager


### Módulos

- **SensorManager** → Leitura de sensores
- **StorageManager** → Gestão do SD
- **BLEManager** → Serviço GATT
- **WiFiManager** → Ligação à rede
- **MQTTManager** → Comunicação MQTT
- **SyncManager** → Envio de dados armazenados
- **ConfigManager** → Parâmetros e credenciais

---

# 👥 Divisão do Trabalho

## Elemento 1 – Firmware

- Implementação sensores
- Armazenamento SD
- BLE GATT Server
- Wi-Fi + MQTT
- Sincronização

## Elemento 2 – Aplicação Android + Backend

- BLE Client
- GUI
- Envio de comandos
- Broker MQTT
- Base de dados
- Backend de receção

---

# 🎯 Objetivos do Projeto

- Implementar comunicação BLE bidirecional
- Implementar sincronização Wi-Fi com MQTT
- Desenvolver arquitetura IoT modular
- Aplicar modelo Store-and-Forward
- Garantir eficiência energética
- Desenvolver aplicação Android com GUI funcional

---

# 📊 Conceitos Aplicados

- IoT Architecture (Perception / Network / Application)
- Edge Computing
- Bluetooth Low Energy (GATT)
- MQTT
- Store-and-Forward Model
- Comunicação assíncrona
- Gestão de energia em dispositivos IoT

---

# 🚀 Estado do Projeto

🔲 Planeamento  
🔲 Implementação Firmware  
🔲 Implementação BLE  
🔲 Implementação MQTT  
🔲 Aplicação Android  
🔲 Testes Integrados  

---

# 📚 Disciplina

Redes Móveis e Internet das Coisas  
Licenciatura/Engenharia  

---

# 🐶 Objetivo Final

Permitir a recolha e análise de dados biomecânicos para avaliar a adaptação do cão à prótese, através de uma solução IoT eficiente, modular e escalável.