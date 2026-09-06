/**
 * مزامنة تطبيق محطة الأمير — استقبال الورديات وكتابتها في Google Sheets.
 *
 * التنصيب:
 * 1) افتح Google Sheet المخصص، ثم Extensions > Apps Script.
 * 2) الصق هذا الكود بالكامل.
 * 3) من القائمة اليسرى: Project Settings > Script Properties > Add script property
 *    باسم SYNC_SECRET وقيمة كلمة سر قوية من اختيارك (يجب أن تطابق SYNC_SECRET في app/build.gradle).
 * 4) Deploy > New deployment > Web app.
 *    - Execute as: Me
 *    - Who has access: Anyone
 * 5) انسخ رابط /exec الناتج وضعه في SYNC_URL داخل app/build.gradle قبل بناء الإصدار الرسمي.
 * 6) لن تُنشأ الشيتات (الورديات / تفاصيل القراءات / الحركات) إلا تلقائيًا عند أول مزامنة ناجحة.
 */

function doPost(e) {
  try {
    var body = JSON.parse(e.postData.contents);
    var expected = PropertiesService.getScriptProperties().getProperty('SYNC_SECRET');
    if (!expected || body.secret !== expected) {
      return jsonOutput({ ok: false, error: 'unauthorized' });
    }
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    upsertShift(ss, body);
    replaceDetails(ss, 'تفاصيل القراءات',
      ['shiftId', 'الطرمبة', 'الوقود', 'القراءة السابقة', 'القراءة الحالية', 'السعر', 'المبيعات'],
      body.shiftId, body.readings || [],
      function (item) { return [body.shiftId, item.pump, item.fuel, item.previous, item.current, item.price, item.sales]; });
    replaceDetails(ss, 'الحركات',
      ['shiftId', 'النوع', 'الاسم', 'المبلغ'],
      body.shiftId, body.movements || [],
      function (item) { return [body.shiftId, arabicType(item.type), item.name, item.amount]; });
    return jsonOutput({ ok: true });
  } catch (err) {
    return jsonOutput({ ok: false, error: String(err) });
  }
}

function doGet() {
  return jsonOutput({ ok: true, message: 'نقطة مزامنة محطة الأمير جاهزة. استخدم POST.' });
}

function getOrCreateSheet(ss, name, headers) {
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
    sheet.appendRow(headers);
  }
  return sheet;
}

function upsertShift(ss, body) {
  var headers = ['shiftId', 'العامل', 'وقت الفتح', 'وقت الإغلاق', 'الحالة', 'المبيعات',
    'المقبوضات', 'النقد المسلّم', 'الديون', 'المخاريج', 'الباقي', 'سبب الفرق', 'النسخة', 'آخر تحديث'];
  var sheet = getOrCreateSheet(ss, 'الورديات', headers);
  var data = sheet.getDataRange().getValues();
  var rowIndex = -1;
  for (var i = 1; i < data.length; i++) {
    if (String(data[i][0]) === String(body.shiftId)) { rowIndex = i + 1; break; }
  }
  var existingRevision = rowIndex > -1 ? Number(data[rowIndex - 1][12] || 0) : -1;
  if (body.revision < existingRevision) return; // بيانات أقدم من الموجود، تجاهلها
  var row = [body.shiftId, body.worker, body.openedAt, body.closedAt, body.status,
    body.sales, body.collections, body.cashDelivered, body.debts, body.expenses,
    body.balance, body.differenceReason, body.revision, new Date()];
  if (rowIndex > -1) sheet.getRange(rowIndex, 1, 1, row.length).setValues([row]);
  else sheet.appendRow(row);
}

function replaceDetails(ss, sheetName, headers, shiftId, items, rowBuilder) {
  var sheet = getOrCreateSheet(ss, sheetName, headers);
  var data = sheet.getDataRange().getValues();
  for (var i = data.length - 1; i >= 1; i--) {
    if (String(data[i][0]) === String(shiftId)) sheet.deleteRow(i + 1);
  }
  items.forEach(function (item) { sheet.appendRow(rowBuilder(item)); });
}

function arabicType(type) {
  if (type === 'COLLECTION') return 'مقبوضات';
  if (type === 'CASH') return 'نقد مسلّم';
  if (type === 'DEBT') return 'ديون';
  return 'مخاريج';
}

function jsonOutput(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}
