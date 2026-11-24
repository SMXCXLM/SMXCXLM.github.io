import * as XLSX from 'xlsx'

function cell(row, key) {
  const value = row[key]
  if (value === null || value === undefined) return ''
  return value
}

/** 导出列表为 Excel */
export function exportExcel(filename, columns, rows) {
  const header = columns.filter(c => c.title).map(c => c.title)
  const body = rows.map(row => columns.filter(c => c.title).map(c => cell(row, c.dataIndex)))
  const sheet = XLSX.utils.aoa_to_sheet([header].concat(body))
  sheet['!cols'] = header.map((h, i) => ({ wch: Math.max(10, Math.min(28, String(h).length * 2 + 4)) }))
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, '数据')
  XLSX.writeFile(book, filename)
}

function esc(value) {
  return String(value === null || value === undefined ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 打印列表（浏览器打印） */
export function printReport(title, columns, rows, options) {
  const opt = options || {}
  const cols = columns.filter(c => c.title)
  const head = cols.map(c => '<th>' + esc(c.title) + '</th>').join('')
  const body = rows.map(row => '<tr>' + cols.map(c => '<td>' + esc(cell(row, c.dataIndex)) + '</td>').join('') + '</tr>').join('')
  const win = window.open('', '_blank', 'width=960,height=700')
  if (!win) return
  win.document.write('<html><head><meta charset="utf-8"><title>' + esc(title) + '</title>'
    + '<style>body{font-family:"Microsoft YaHei",Arial;padding:16px;color:#222}'
    + 'h1{font-size:18px;text-align:center;margin:0 0 6px}'
    + '.sub{text-align:center;color:#666;font-size:12px;margin-bottom:10px}'
    + 'table{width:100%;border-collapse:collapse;font-size:12px}'
    + 'th,td{border:1px solid #999;padding:5px 6px;text-align:center}'
    + 'th{background:#eee}'
    + '.summary{margin-top:8px;font-size:12px;text-align:right}'
    + '@media print{@page{size:A4 landscape;margin:8mm}}'
    + '</style></head><body><h1>' + esc(title) + '</h1>'
    + '<div class="sub">' + esc(opt.subtitle || '') + '</div>'
    + '<table><thead><tr>' + head + '</tr></thead><tbody>' + body + '</tbody></table>'
    + '<div class="summary">' + esc(opt.summary || '') + '</div>'
    + '</body></html>')
  win.document.close()
  setTimeout(() => { win.focus(); win.print() }, 300)
}
