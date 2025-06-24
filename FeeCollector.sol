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
