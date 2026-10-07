'use strict';
require('dotenv').config();
const fs = require('fs');
const path = require('path');
const db = require('../src/db');
const target = process.argv[2] || path.join(__dirname,'../../backups','peers-' + new Date().toISOString().replace(/[:.]/g,'-') + '.db');
fs.mkdirSync(path.dirname(target),{recursive:true,mode:0o700});
db.backup(target).then(() => {
  try { fs.chmodSync(target,0o600); } catch {}
  console.log('SQLite backup created: ' + target); db.close();
}).catch(err => {
  console.error('SQLite backup failed: ' + err.message); db.close(); process.exitCode = 1;
});
