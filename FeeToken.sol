// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract FeeToken is ERC20 {
    address public immutable feeCollector;
    uint256 public feePercent = 1; // 1%

    constructor(address _feeCollector) ERC20("FeeToken", "FEE") {
        feeCollector = _feeCollector;
        _mint(msg.sender, 1000000 * 10 ** decimals());
    }

    function _transfer(address sender, address recipient, uint256 amount) internal override {
        uint256 fee = (amount * feePercent) / 100;
        uint256 amountAfterFee = amount - fee;
        super._transfer(sender, feeCollector, fee);
        super._transfer(sender, recipient, amountAfterFee);
    }
}
