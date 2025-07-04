// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract FeeCollector {
    address public immutable owner;

    constructor(address _owner) {
        owner = _owner;
    }

    // Accept ETH payments (from minting, services, etc.)
    receive() external payable {}

    // Only owner can withdraw
    function withdraw() external {
        require(msg.sender == owner, "Not owner");
        payable(owner).transfer(address(this).balance);
    }
}
