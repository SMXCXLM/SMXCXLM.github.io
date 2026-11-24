const fs = require('fs')
const path = require('path')

const dist = path.join(__dirname, '..', 'dist')
const index = path.join(dist, 'index.html')
const notFound = path.join(dist, '404.html')

if (!fs.existsSync(index)) {
  throw new Error('dist/index.html not found; run vue-cli-service build first')
}

fs.copyFileSync(index, notFound)
fs.writeFileSync(path.join(dist, '.nojekyll'), '')
console.log('Created dist/404.html and dist/.nojekyll for GitHub Pages')
