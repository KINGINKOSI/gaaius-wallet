const Web3 = require('web3');
const fs = require('fs');

const web3 = new Web3('http://localhost:8545');

const abi = ;
const bytecode = '0x';

async function deploy() {
    const accounts = await web3.eth.getAccounts();
    console.log('Deploying from account:', accounts[0]);

    const contract = new web3.eth.Contract(abi);

    const deployedContract = await contract.deploy({ data: bytecode })
        .send({ from: accounts[0], gas: 1500000 });

    console.log('Contract deployed at:', deployedContract.options.address);

    // Save contract address to file
    fs.writeFileSync('contract-address.txt', deployedContract.options.address);
    console.log('Contract address saved to contract-address.txt');
    process.exit(0);
}

deploy().catch(console.error);
