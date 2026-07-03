# Farmworker Wallet OS - Key Management
Manage private keys in web or mobile (iOS and Android) platforms

## Quick Start

* Clone this project
* Navigate to the project root from a command line Terminal e.g. `cd ~/Workspaces/Github/farmworker-wallet-os-key-management`
* Install key management npm and react-native dependencies
* Run `cd ../javascriptsource/keymanagement`
* Run `yarn install`
* Download [Studio Pro 10.24.22](https://marketplace.mendix.com/link/studiopro/)
* Open `KeyManagement.mpr` from Studio Pro
* Run the project from Studio Pro by clicking Run / Run Locally
* Create a custom React Native application from Native Template `./resources/nativeTemplate`
* Install and run the application on your mobile device
* Log in using user name, for example `Alice` or  `Bob`

## Run Native app
* `cd ~/resources/nativeTemplate`
* Install Node v22 `nvm install 22`
* Install npm dependencies 
    * `npm i --legacy-peer-deps`
    * `npm run configure`



## Build a Mendix Native App Locally Manually


### Tool setup
```
nvm install --lts
nvm use --lts
npm i --legacy-peer-deps
```
### Install npm and react-native dependencies
```
cd ./farmworker-wallet-os-key-management/resources/nativeTemplate

npm install --legacy-peer-deps
npm run configure
```

### Building an iPhone App with XCode

#### Install iOS dependencies
```
cd ios
pod install --repo-update
```
