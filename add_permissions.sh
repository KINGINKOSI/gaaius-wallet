#!/bin/bash
set -e

echo "Initializing GAAIUS wallet fee collection environment..."

# 1. Initialize Node.js project (skip if package.json exists)
if [ ! -f package.json ]; then
  npm init -y
fi

# 2. Install Hardhat and dependencies
npm install --save-dev hardhat @nomiclabs/hardhat-ethers ethers

# 3. Create Hardhat project if not exists
if [ ! -f hardhat.config.js ]; then
  npx hardhat init --force
fi

# 4. Create FeeCollector.sol contract
mkdir -p contracts
cat > contracts/FeeCollector.sol << 'EOF'
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract FeeCollector {
    address public owner;

    constructor() {
        owner = msg.sender;
    }

    function collectFee() external payable {
        // Fees sent with this transaction are stored in this contract
    }

    function withdraw() external {
        require(msg.sender == owner, "Not owner");
        payable(owner).transfer(address(this).balance);
    }
}
EOF
echo "Created contracts/FeeCollector.sol"

# 5. Create deployment script
mkdir -p scripts
cat > scripts/deploy_fee_collector.js << 'EOF'
async function main() {
  const [deployer] = await ethers.getSigners();
  console.log("Deploying contracts with the account:", deployer.address);

  const FeeCollector = await ethers.getContractFactory("FeeCollector");
  const feeCollector = await FeeCollector.deploy();

  await feeCollector.deployed();

  console.log("FeeCollector deployed to:", feeCollector.address);
}

main()
  .then(() => process.exit(0))
  .catch(error => {
    console.error(error);
    process.exit(1);
  });
EOF
echo "Created scripts/deploy_fee_collector.js"

# 6. Provide placeholder for old fee address replacement
echo "Replace OLD_FEE_ADDRESS in your code with the deployed FeeCollector address after deployment."
echo "Example sed command (replace OLD_FEE_ADDRESS with actual old address):"
echo "grep -rl 'OLD_FEE_ADDRESS' . | xargs sed -i 's|OLD_FEE_ADDRESS|<DEPLOYED_FEE_COLLECTOR_ADDRESS>|g'"

# 7. Instructions
cat << INSTRUCTIONS

Setup complete!

Next steps:

1. Configure your Hardhat network settings in hardhat.config.js (e.g., for Goerli or mainnet).

2. Deploy the FeeCollector contract with:
   npx hardhat run scripts/deploy_fee_collector.js --network <network-name>

3. Copy the deployed contract address from the console output.

4. Replace all old fee addresses in your code with the new contract address using the sed command above.

5. Modify your wallet code to send fee payments to the FeeCollector contract address.

6. Test thoroughly on testnet before mainnet deployment.

INSTRUCTIONS
