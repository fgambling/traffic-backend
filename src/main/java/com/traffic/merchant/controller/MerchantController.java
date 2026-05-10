package com.traffic.merchant.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.device.entity.TrafficFact;
import com.traffic.device.mapper.TrafficFactMapper;
import com.traffic.merchant.dto.DashboardResponse;
import com.traffic.merchant.dto.ProfileResponse;
import com.traffic.merchant.dto.StayAnalysisResponse;
import com.traffic.merchant.dto.TrendPoint;
import com.traffic.merchant.entity.Merchant;
import com.traffic.merchant.entity.MerchantBusinessInfo;
import com.traffic.merchant.mapper.MerchantBusinessInfoMapper;
import com.traffic.merchant.mapper.MerchantMapper;
import com.traffic.merchant.service.MerchantService;
import com.traffic.security.JwtPrincipal;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.FontSelector;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家端接口
 * 所有接口需要JWT认证（role=merchant）
 */
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

  private final MerchantService           merchantService;
  private final MerchantMapper            merchantMapper;
  private final MerchantBusinessInfoMapper businessInfoMapper;
  private final TrafficFactMapper         trafficFactMapper;
  private final PasswordEncoder           passwordEncoder;

  /**
   * 获取今日客流看板
   * GET /api/merchant/dashboard
   */
  @GetMapping("/dashboard")
  public R<DashboardResponse> dashboard(@AuthenticationPrincipal JwtPrincipal principal) {
    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    DashboardResponse data = merchantService.getDashboard(principal.getMerchantId());
    return R.ok(data);
  }

  /**
   * 获取客流趋势
   * GET /api/merchant/trend?type=hour&start=2026-04-17&end=2026-04-17
   *
   * @param type  粒度：hour（默认）/ day / week / month
   * @param start 开始日期，格式 yyyy-MM-dd，不传时按 type 取默认回溯
   * @param end   结束日期，格式 yyyy-MM-dd，不传时为今天
   */
  @GetMapping("/trend")
  public R<List<TrendPoint>> trend(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(defaultValue = "hour") String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt,
      @RequestParam(required = false) String date, // minute粒度：YYYY-MM-DD，避免ISO时间戳URL编码问题
      @RequestParam(required = false) Integer hour) { // minute粒度：0-23

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    // type=minute 优先使用 date+hour 参数（简单安全，无需编码），
    // 也兼容 startDt/endDt（直接传入时使用）
    if ("minute".equals(type) && date != null && hour != null) {
      LocalDate queryDate = LocalDate.parse(date);
      startDt = queryDate.atTime(hour, 0, 0);
      endDt = queryDate.atTime(hour, 59, 59);
    }

    List<TrendPoint> data = merchantService.getTrend(principal.getMerchantId(), type, start, end, startDt, endDt);
    return R.ok(data);
  }

  /**
   * 获取停留时长分析（分布 + 趋势 + 与前期对比）
   * GET /api/merchant/stay?type=day&start=2026-04-15&end=2026-04-21
   *
   * @param type  粒度：hour / day / week
   * @param start 开始日期，不传时按 type 给默认回溯
   * @param end   结束日期，不传时为今天
   */
  @GetMapping("/stay")
  public R<StayAnalysisResponse> stayAnalysis(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(defaultValue = "day") String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) Integer hour,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt) {

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    StayAnalysisResponse data = merchantService.getStayAnalysis(
        principal.getMerchantId(), type, start, end, hour, startDt, endDt);
    return R.ok(data);
  }

  /**
   * 获取用户画像详情
   * GET /api/merchant/profile?start=2026-04-17&end=2026-04-17
   *
   * @param start 开始日期，格式 yyyy-MM-dd，不传时默认今天
   * @param end   结束日期，格式 yyyy-MM-dd，不传时默认今天
   */
  /**
   * 获取当前账号下所有门店（同手机号）
   * GET /api/merchant/stores
   * 返回：{ phone, stores: [{ merchantId, name, address, licenseNo }] }
   */
  @GetMapping("/stores")
  public R<Map<String, Object>> stores(@AuthenticationPrincipal JwtPrincipal principal) {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);

    Merchant current = merchantMapper.selectById(principal.getMerchantId());
    if (current == null) throw new BusinessException(404, "商家不存在");

    String phone = current.getContactPhone();
    List<Map<String, Object>> list;

    if (phone == null || phone.isBlank()) {
      // 无手机号则只返回自身
      list = List.of(buildStoreItem(current));
    } else {
      list = merchantMapper.selectList(
              new LambdaQueryWrapper<Merchant>()
                      .eq(Merchant::getContactPhone, phone)
                      .ne(Merchant::getStatus, 2))
          .stream()
          .map(this::buildStoreItem)
          .collect(Collectors.toList());
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("phone",   phone);
    result.put("stores",  list);
    return R.ok(result);
  }

  /**
   * 修改手机号
   * PUT /api/merchant/phone
   * body: { phone }
   */
  @PutMapping("/phone")
  public R<Void> changePhone(@AuthenticationPrincipal JwtPrincipal principal,
                              @RequestBody Map<String, String> body) {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    String newPhone = body.get("phone");
    if (!StringUtils.hasText(newPhone) || !newPhone.matches("^1\\d{10}$"))
      throw new BusinessException(400, "手机号格式不正确");
    merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
        .eq(Merchant::getId, principal.getMerchantId())
        .set(Merchant::getContactPhone, newPhone));
    return R.ok(null);
  }

  /**
   * 修改密码
   * PUT /api/merchant/password
   * body: { oldPassword, newPassword }
   */
  @PutMapping("/password")
  public R<Void> changePassword(@AuthenticationPrincipal JwtPrincipal principal,
                                @RequestBody Map<String, String> body) {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);

    String oldPwd = body.get("oldPassword");
    String newPwd = body.get("newPassword");
    if (!StringUtils.hasText(oldPwd) || !StringUtils.hasText(newPwd))
      throw new BusinessException(400, "参数不能为空");
    if (newPwd.length() < 6)
      throw new BusinessException(400, "新密码不能少于6位");

    Merchant m = merchantMapper.selectById(principal.getMerchantId());
    if (m == null) throw new BusinessException(404, "商家不存在");
    if (!StringUtils.hasText(m.getPassword()) || !passwordEncoder.matches(oldPwd, m.getPassword()))
      throw new BusinessException(400, "原密码错误");

    merchantMapper.update(null, new LambdaUpdateWrapper<Merchant>()
        .eq(Merchant::getId, principal.getMerchantId())
        .set(Merchant::getPassword, passwordEncoder.encode(newPwd)));
    return R.ok(null);
  }

  /**
   * 获取门店业务信息（菜单/促销/营业时间/目标客群）
   * GET /api/merchant/business-info
   */
  @GetMapping("/business-info")
  public R<MerchantBusinessInfo> getBusinessInfo(@AuthenticationPrincipal JwtPrincipal principal) {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    MerchantBusinessInfo info = businessInfoMapper.findByMerchant(principal.getMerchantId());
    return R.ok(info != null ? info : new MerchantBusinessInfo());
  }

  /**
   * 保存门店业务信息（UPSERT）
   * PUT /api/merchant/business-info
   */
  @PutMapping("/business-info")
  public R<Void> saveBusinessInfo(@AuthenticationPrincipal JwtPrincipal principal,
                                  @RequestBody MerchantBusinessInfo body) {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    Integer merchantId = principal.getMerchantId();
    MerchantBusinessInfo existing = businessInfoMapper.findByMerchant(merchantId);
    if (existing == null) {
      body.setId(null);
      body.setMerchantId(merchantId);
      businessInfoMapper.insert(body);
    } else {
      body.setId(existing.getId());
      body.setMerchantId(merchantId);
      businessInfoMapper.updateById(body);
    }
    return R.ok(null);
  }

  /**
   * 导出近30天客流报表（Excel）
   * GET /api/merchant/export/report
   */
  @GetMapping("/export/report")
  public void exportReport(@AuthenticationPrincipal JwtPrincipal principal,
                           HttpServletResponse response) throws IOException {
    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    Integer merchantId = principal.getMerchantId();
    Merchant m = merchantMapper.selectById(merchantId);
    String merchantName = m != null ? m.getName() : String.valueOf(merchantId);

    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    LocalDateTime from = today.minusDays(29).atStartOfDay();
    LocalDateTime to   = today.plusDays(1).atStartOfDay();

    List<TrafficFact> facts = trafficFactMapper.findTodayByMerchant(merchantId, from, to);

    // 按日期聚合
    Map<LocalDate, Map<String, Object>> byDate = new TreeMap<>(Comparator.reverseOrder());
    for (TrafficFact f : facts) {
      if (f.getTimeBucket() == null) continue;
      LocalDate date = f.getTimeBucket().toLocalDate();
      byDate.computeIfAbsent(date, k -> new LinkedHashMap<>(Map.of(
          "enter", 0, "pass", 0, "male", 0, "female", 0,
          "staySeconds", 0, "stayCount", 0, "peakHour", -1, "peakCount", 0)));
      Map<String, Object> row = byDate.get(date);
      add(row, "enter",       f.getEnterCount());
      add(row, "pass",        f.getPassCount());
      add(row, "male",        f.getGenderMale());
      add(row, "female",      f.getGenderFemale());
      add(row, "staySeconds", f.getTotalStaySeconds());
      add(row, "stayCount",   f.getStayCount());
      int h = f.getTimeBucket().getHour();
      if (f.getEnterCount() > (int) row.get("peakCount")) {
        row.put("peakCount", f.getEnterCount());
        row.put("peakHour", h);
      }
    }

    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      CellStyle headerStyle = wb.createCellStyle();
      Font headerFont = wb.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      Sheet sheet = wb.createSheet("近30天客流报表");
      sheet.setColumnWidth(0, 4000); sheet.setColumnWidth(1, 3000);
      sheet.setColumnWidth(2, 3000); sheet.setColumnWidth(3, 3500);
      sheet.setColumnWidth(4, 3500); sheet.setColumnWidth(5, 3500);

      String[] headers = {"日期", "进店人数", "经过人数", "高峰时段", "女性占比", "平均停留(分钟)"};
      Row hRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell c = hRow.createCell(i);
        c.setCellValue(headers[i]);
        c.setCellStyle(headerStyle);
      }

      int rowIdx = 1;
      for (Map.Entry<LocalDate, Map<String, Object>> entry : byDate.entrySet()) {
        LocalDate date = entry.getKey();
        Map<String, Object> d = entry.getValue();
        int enter  = (int) d.get("enter");
        int pass   = (int) d.get("pass");
        int male   = (int) d.get("male");
        int female = (int) d.get("female");
        int stayS  = (int) d.get("staySeconds");
        int stayC  = (int) d.get("stayCount");
        int peak   = (int) d.get("peakHour");

        double femaleRatio = (male + female) > 0 ? female * 100.0 / (male + female) : 0;
        double avgStayMin  = stayC > 0 ? stayS / 60.0 / stayC : 0;
        String peakStr     = peak >= 0 ? peak + ":00-" + (peak + 1) + ":00" : "--";

        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(date.toString());
        row.createCell(1).setCellValue(enter);
        row.createCell(2).setCellValue(pass);
        row.createCell(3).setCellValue(peakStr);
        row.createCell(4).setCellValue(String.format("%.1f%%", femaleRatio));
        row.createCell(5).setCellValue(String.format("%.1f", avgStayMin));
      }

      String filename = URLEncoder.encode(merchantName + "_客流报表.xlsx", StandardCharsets.UTF_8);
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
      wb.write(response.getOutputStream());
    }
  }

  /**
   * 导出趋势/画像报表
   * GET /api/merchant/export/trend?format=excel&scope=trend&type=hour&start=...
   */
  @GetMapping("/export/trend")
  public void exportTrend(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(defaultValue = "excel") String format,
      @RequestParam(defaultValue = "trend") String scope,
      @RequestParam(defaultValue = "hour") String type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate profileStart,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate profileEnd,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime profileStartDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime profileEndDt,
      HttpServletResponse response) throws IOException {

    if (!"merchant".equals(principal.getRole())) throw new BusinessException(ErrorCode.FORBIDDEN);
    Integer merchantId = principal.getMerchantId();
    Merchant m = merchantMapper.selectById(merchantId);
    String merchantName = m != null ? m.getName() : String.valueOf(merchantId);

    List<TrendPoint> trendPoints = !"profile".equals(scope)
        ? merchantService.getTrend(merchantId, type, start, end, startDt, endDt)
        : List.of();

    ProfileResponse profile = null;
    if (!"trend".equals(scope)) {
      LocalDate      ps  = profileStart   != null ? profileStart   : start;
      LocalDate      pe  = profileEnd     != null ? profileEnd     : end;
      LocalDateTime  psd = profileStartDt != null ? profileStartDt : startDt;
      LocalDateTime  ped = profileEndDt   != null ? profileEndDt   : endDt;
      profile = merchantService.getProfile(merchantId, ps, pe, psd, ped);
    }

    String fileBase = merchantName + "_客流数据";
    if ("pdf".equals(format)) {
      exportTrendAsPdf(response, fileBase, trendPoints, profile, type);
    } else {
      exportTrendAsExcel(response, fileBase, trendPoints, profile, type, scope);
    }
  }

  private String trendTypeLabel(String type) {
    return switch (type) {
      case "hour"  -> "小时";
      case "day"   -> "日期";
      case "week"  -> "周";
      case "month" -> "月份";
      default      -> "时间";
    };
  }

  private String fmtSecsStr(int secs) {
    if (secs <= 0) return "--";
    int min = secs / 60, sec = secs % 60;
    return sec > 0 ? min + "分" + sec + "秒" : min + "分钟";
  }

  private List<String[]> buildProfileExportRows(ProfileResponse p) {
    List<String[]> rows = new ArrayList<>();
    if (p == null) return rows;
    int total = p.getTotalEnter() > 0 ? p.getTotalEnter() : 1;
    rows.add(new String[]{"进店人次", String.valueOf(p.getTotalEnter())});
    rows.add(new String[]{"平均停留", fmtSecsStr(p.getAvgStaySeconds())});
    rows.add(new String[]{"新客占比", String.format("%.1f%%", p.getNewCustomerCount() * 100.0 / total)});
    int g = p.getGenderMale() + p.getGenderFemale();
    if (g > 0) {
      rows.add(new String[]{"男性占比", String.format("%.1f%%", p.getGenderMale()   * 100.0 / g)});
      rows.add(new String[]{"女性占比", String.format("%.1f%%", p.getGenderFemale() * 100.0 / g)});
    }
    int at = p.getAgeUnder18() + p.getAge1860() + p.getAgeOver60();
    if (at > 0) {
      rows.add(new String[]{"年龄 <18",   String.format("%.1f%%", p.getAgeUnder18() * 100.0 / at)});
      rows.add(new String[]{"年龄 18-60", String.format("%.1f%%", p.getAge1860()    * 100.0 / at)});
      rows.add(new String[]{"年龄 >60",   String.format("%.1f%%", p.getAgeOver60()  * 100.0 / at)});
    }
    if (p.getUpperShort()        > 0) rows.add(new String[]{"上衣-短袖", String.format("%.1f%%", p.getUpperShort()        * 100.0 / total)});
    if (p.getUpperLong()         > 0) rows.add(new String[]{"上衣-长袖", String.format("%.1f%%", p.getUpperLong()         * 100.0 / total)});
    if (p.getUpperCoat()         > 0) rows.add(new String[]{"上衣-外套", String.format("%.1f%%", p.getUpperCoat()         * 100.0 / total)});
    if (p.getLowerTrousers()     > 0) rows.add(new String[]{"下装-长裤", String.format("%.1f%%", p.getLowerTrousers()     * 100.0 / total)});
    if (p.getLowerShorts()       > 0) rows.add(new String[]{"下装-短裤", String.format("%.1f%%", p.getLowerShorts()       * 100.0 / total)});
    if (p.getLowerSkirt()        > 0) rows.add(new String[]{"下装-裙子", String.format("%.1f%%", p.getLowerSkirt()        * 100.0 / total)});
    if (p.getBagBackpack()       > 0) rows.add(new String[]{"背包",      String.format("%.1f%%", p.getBagBackpack()       * 100.0 / total)});
    if (p.getAccessoryGlasses()  > 0) rows.add(new String[]{"眼镜",      String.format("%.1f%%", p.getAccessoryGlasses()  * 100.0 / total)});
    if (p.getAccessoryHat()      > 0) rows.add(new String[]{"帽子",      String.format("%.1f%%", p.getAccessoryHat()      * 100.0 / total)});
    return rows;
  }

  private void exportTrendAsExcel(HttpServletResponse response, String fileBase,
      List<TrendPoint> trendPoints, ProfileResponse profile, String type, String scope) throws IOException {

    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      // ── 表头样式（蓝底加粗居中，带边框）──────────────────────────
      CellStyle headerSt = wb.createCellStyle();
      org.apache.poi.ss.usermodel.Font hFont = wb.createFont();
      hFont.setBold(true);
      hFont.setFontHeightInPoints((short) 11);
      headerSt.setFont(hFont);
      headerSt.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
      headerSt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerSt.setAlignment(HorizontalAlignment.CENTER);
      headerSt.setVerticalAlignment(VerticalAlignment.CENTER);
      applyBorderThin(headerSt);

      // ── 数据文字样式（左对齐，带边框）────────────────────────────
      CellStyle textSt = wb.createCellStyle();
      org.apache.poi.ss.usermodel.Font dFont = wb.createFont();
      dFont.setFontHeightInPoints((short) 10);
      textSt.setFont(dFont);
      textSt.setVerticalAlignment(VerticalAlignment.CENTER);
      applyBorderThin(textSt);

      // ── 数据数字样式（右对齐，带边框）────────────────────────────
      CellStyle numSt = wb.createCellStyle();
      numSt.cloneStyleFrom(textSt);
      numSt.setAlignment(HorizontalAlignment.RIGHT);

      // ── 客流趋势 Sheet ───────────────────────────────────────────
      if (!"profile".equals(scope) && !trendPoints.isEmpty()) {
        Sheet sh = wb.createSheet("客流趋势");
        sh.setDefaultRowHeightInPoints(20);
        sh.setColumnWidth(0, 7000);   // 时间列：约 27 字符宽
        sh.setColumnWidth(1, 4000);   // 人数列

        Row hRow = sh.createRow(0);
        hRow.setHeightInPoints(26);
        xlCell(hRow, 0, trendTypeLabel(type), headerSt);
        xlCell(hRow, 1, "进店人数",            headerSt);

        int ri = 1;
        for (TrendPoint p : trendPoints) {
          Row r = sh.createRow(ri++);
          r.setHeightInPoints(20);
          xlCell(r, 0, p.getTimeLabel(),             textSt);
          xlCell(r, 1, p.getEnterCount(),            numSt);
        }
        sh.createFreezePane(0, 1);
      }

      // ── 客群画像 Sheet ───────────────────────────────────────────
      if (!"trend".equals(scope) && profile != null) {
        Sheet sh = wb.createSheet("客群画像");
        sh.setDefaultRowHeightInPoints(20);
        sh.setColumnWidth(0, 5500);   // 指标列
        sh.setColumnWidth(1, 5000);   // 数值列

        Row hRow = sh.createRow(0);
        hRow.setHeightInPoints(26);
        xlCell(hRow, 0, "指标", headerSt);
        xlCell(hRow, 1, "数值", headerSt);

        List<String[]> pRows = buildProfileExportRows(profile);
        for (int i = 0; i < pRows.size(); i++) {
          Row r = sh.createRow(i + 1);
          r.setHeightInPoints(20);
          xlCell(r, 0, pRows.get(i)[0], textSt);
          xlCell(r, 1, pRows.get(i)[1], numSt);
        }
        sh.createFreezePane(0, 1);
      }

      String fn = URLEncoder.encode(fileBase + ".xlsx", StandardCharsets.UTF_8);
      response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fn);
      wb.write(response.getOutputStream());
    }
  }

  private void applyBorderThin(CellStyle cs) {
    cs.setBorderTop(BorderStyle.THIN);
    cs.setBorderBottom(BorderStyle.THIN);
    cs.setBorderLeft(BorderStyle.THIN);
    cs.setBorderRight(BorderStyle.THIN);
  }

  private void xlCell(Row row, int col, String value, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(value); c.setCellStyle(style);
  }

  private void xlCell(Row row, int col, int value, CellStyle style) {
    Cell c = row.createCell(col); c.setCellValue(value); c.setCellStyle(style);
  }

  private void exportTrendAsPdf(HttpServletResponse response, String fileBase,
      List<TrendPoint> trendPoints, ProfileResponse profile, String type) throws IOException {

    String fn = URLEncoder.encode(fileBase + ".pdf", StandardCharsets.UTF_8);
    response.setContentType("application/pdf");
    response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fn);

    // ── FontSelector：ASCII 用 Helvetica，CJK 用 STSong-Light ────
    // 这样日期 "2026-04-17" 等 ASCII 内容不会被 CJK 编码渲染成挤在一起的全角字符
    BaseFont cjk;
    try {
      cjk = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
    } catch (Exception e) {
      cjk = null;
    }

    FontSelector titleSel   = makeSelector(cjk, 18, com.lowagie.text.Font.BOLD, null);
    FontSelector sectionSel = makeSelector(cjk, 13, com.lowagie.text.Font.BOLD, null);
    FontSelector hdrSel     = makeSelector(cjk, 11, com.lowagie.text.Font.BOLD, PDF_HDR_FG);
    FontSelector dataSel    = makeSelector(cjk, 11, com.lowagie.text.Font.NORMAL, null);

    Document doc = new Document(PageSize.A4, 60, 60, 70, 60);
    PdfWriter.getInstance(doc, response.getOutputStream());
    doc.open();

    // ── 标题 ─────────────────────────────────────────────────────
    Paragraph title = new Paragraph(titleSel.process(fileBase));
    title.setSpacingAfter(20);
    doc.add(title);

    // ── 客流趋势表 ───────────────────────────────────────────────
    if (!trendPoints.isEmpty()) {
      Paragraph sectionLabel = new Paragraph(sectionSel.process("客流趋势"));
      sectionLabel.setSpacingAfter(8);
      doc.add(sectionLabel);

      PdfPTable table = new PdfPTable(2);
      table.setWidthPercentage(100);
      table.setWidths(new float[]{3.5f, 1.5f});
      table.setSpacingAfter(20);

      table.addCell(pdfHdrCell(hdrSel.process(trendTypeLabel(type))));
      table.addCell(pdfHdrCell(hdrSel.process("进店人数")));
      for (int i = 0; i < trendPoints.size(); i++) {
        TrendPoint p = trendPoints.get(i);
        java.awt.Color bg = i % 2 == 1 ? PDF_ROW_ALT : null;
        table.addCell(pdfDataCell(dataSel.process(p.getTimeLabel()),                  false, bg));
        table.addCell(pdfDataCell(dataSel.process(String.valueOf(p.getEnterCount())), true,  bg));
      }
      doc.add(table);
    }

    // ── 客群画像表 ───────────────────────────────────────────────
    if (profile != null) {
      Paragraph sectionLabel = new Paragraph(sectionSel.process("客群画像"));
      sectionLabel.setSpacingAfter(8);
      doc.add(sectionLabel);

      PdfPTable table = new PdfPTable(2);
      table.setWidthPercentage(65);
      table.setWidths(new float[]{3f, 2f});

      table.addCell(pdfHdrCell(hdrSel.process("指标")));
      table.addCell(pdfHdrCell(hdrSel.process("数值")));
      List<String[]> pRows = buildProfileExportRows(profile);
      for (int i = 0; i < pRows.size(); i++) {
        java.awt.Color bg = i % 2 == 1 ? PDF_ROW_ALT : null;
        table.addCell(pdfDataCell(dataSel.process(pRows.get(i)[0]), false, bg));
        table.addCell(pdfDataCell(dataSel.process(pRows.get(i)[1]), true,  bg));
      }
      doc.add(table);
    }

    doc.close();
  }

  private FontSelector makeSelector(BaseFont cjk, float size, int style, java.awt.Color color) {
    FontSelector sel = new FontSelector();
    com.lowagie.text.Font latin = color != null
        ? new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, size, style, color)
        : new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, size, style);
    sel.addFont(latin);
    if (cjk != null) {
      com.lowagie.text.Font cjkFont = color != null
          ? new com.lowagie.text.Font(cjk, size, style, color)
          : new com.lowagie.text.Font(cjk, size, style);
      sel.addFont(cjkFont);
    }
    return sel;
  }

  private static final java.awt.Color PDF_HDR_BG   = new java.awt.Color(41,  105, 176);
  private static final java.awt.Color PDF_HDR_FG   = new java.awt.Color(255, 255, 255);
  private static final java.awt.Color PDF_ROW_ALT  = new java.awt.Color(240, 245, 255);

  private PdfPCell pdfHdrCell(Phrase phrase) {
    PdfPCell cell = new PdfPCell(phrase);
    cell.setBackgroundColor(PDF_HDR_BG);
    cell.setPadding(10f);
    cell.setLeading(0f, 1.5f);
    cell.setMinimumHeight(32f);
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    return cell;
  }

  private PdfPCell pdfDataCell(Phrase phrase, boolean rightAlign, java.awt.Color bg) {
    PdfPCell cell = new PdfPCell(phrase);
    cell.setPadding(9f);
    cell.setLeading(0f, 1.5f);
    cell.setMinimumHeight(28f);
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    if (rightAlign) cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
    if (bg != null) cell.setBackgroundColor(bg);
    return cell;
  }

  private void add(Map<String, Object> row, String key, int val) {
    row.put(key, (int) row.get(key) + val);
  }

  private Map<String, Object> buildStoreItem(Merchant m) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("merchantId", m.getId());
    item.put("name",       m.getName());
    item.put("address",    m.getAddress());
    item.put("licenseNo",  m.getLicenseNo());
    return item;
  }

  @GetMapping("/profile")
  public R<ProfileResponse> profile(
      @AuthenticationPrincipal JwtPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDt,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDt) {

    if (!"merchant".equals(principal.getRole())) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    ProfileResponse data = merchantService.getProfile(principal.getMerchantId(), start, end, startDt, endDt);
    return R.ok(data);
  }
}
