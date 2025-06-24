// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";

interface IFeeCollector {
    function owner() external view returns (address);
}

contract ArtNFT is ERC721 {
    address public immutable feeCollector;
    uint256 public mintFee = 0.01 ether;
    uint256 public transferFee = 0.005 ether;

    constructor(address _feeCollector) ERC721("MyArt", "ART") {
        feeCollector = _feeCollector;
    }

    function mint(address to, uint256 tokenId) external payable {
        require(msg.value >= mintFee, "Mint fee required");
        _mint(to, tokenId);
        // Forward fee to collector
        payable(feeCollector).transfer(msg.value);
    }

    function _transfer(address from, address to, uint256 tokenId) internal override {
        require(msg.sender == from || msg.sender == ownerOf(tokenId), "Not authorized");
        require(msg.value >= transferFee, "Transfer fee required");
        super._transfer(from, to, tokenId);
        // Forward transfer fee to collector
        payable(feeCollector).transfer(msg.value);
    }

    // Optional: Royalty support (EIP-2981)
    function royaltyInfo(uint256, uint256 salePrice) external view returns (address, uint256) {
        uint256 royaltyAmount = (salePrice * 5) / 100; // 5% royalty
        return (feeCollector, royaltyAmount);
    }
}
