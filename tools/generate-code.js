#!/usr/bin/env node
/**
 * smart-bridge 激活码生成器
 * 
 * 用法: node generate-code.js <machineId>
 * 示例: node generate-code.js A1B2C3D4E5F6G7H8
 * 
 * ClawClaw.tech 代理商工具
 * 激活码价格: ¥499 (用户) / ¥50 (代理商批发)
 */

const crypto = require('crypto');

const SALT = 'sm4rt-br1dge-pr0-2026-cl4w';

function generateCode(machineId) {
    const hash = crypto.createHash('sha256')
        .update(machineId + SALT)
        .digest('hex')
        .substring(0, 16)
        .toUpperCase();
    return hash.match(/.{4}/g).join('-');
}

const machineId = process.argv[2];
if (!machineId) {
    console.log('用法: node generate-code.js <machineId>');
    console.log('示例: node generate-code.js A1B2C3D4E5F6G7H8');
    process.exit(1);
}

const code = generateCode(machineId);
console.log('=================================');
console.log('  ClawClaw.tech 激活码生成器');
console.log('=================================');
console.log(`机器码: ${machineId.toUpperCase()}`);
console.log(`激活码: ${code}`);
console.log(`售价:   ¥499`);
console.log('=================================');
