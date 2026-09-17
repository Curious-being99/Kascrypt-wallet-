const crypto = require('crypto');
const h = crypto.createHash('sha256').update('test').digest('hex');
console.log(h);
