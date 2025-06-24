require('dotenv').config();
const { ethers } = require("hardhat");

async function main() {
    const feeCollectorAddress = process.env.FEE_COLLECTOR_ADDRESS;
    if (!feeCollectorAddress || !/^0x[a-fA-F0-9]{40}$/.test(feeCollectorAddress)) {
        throw new Error("FEE_COLLECTOR_ADDRESS is missing or invalid in .env");
    }

    // Deploy FeeCollector
    const FeeCollector = await ethers.getContractFactory("FeeCollector");
    const feeCollector = await FeeCollector.deploy(feeCollectorAddress);
    await feeCollector.deployed();
    console.log("FeeCollector deployed at:", feeCollector.address);

    // Deploy ArtNFT with FeeCollector address
    const ArtNFT = await ethers.getContractFactory("ArtNFT");
    const artNFT = await ArtNFT.deploy(feeCollector.address);
    await artNFT.deployed();
    console.log("ArtNFT deployed at:", artNFT.address);

    // Deploy FeeToken with FeeCollector address
    const FeeToken = await ethers.getContractFactory("FeeToken");
    const feeToken = await FeeToken.deploy(feeCollector.address);
    await feeToken.deployed();
    console.log("FeeToken deployed at:", feeToken.address);
}

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
