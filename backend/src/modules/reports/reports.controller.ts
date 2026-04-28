import { Body, Controller, Get, Param, Post, Put } from '@nestjs/common';

@Controller()
export class ReportsController {
  @Post('reports/channel')
  createReport(@Body() body: unknown) {
    return {
      id: 'report-demo',
      status: 'PENDING',
      ...(body as object),
    };
  }

  @Get('admin/reports')
  listReports() {
    return [];
  }

  @Put('admin/reports/:id/status')
  updateStatus(@Param('id') id: string, @Body() body: { status: string }) {
    return { id, status: body.status };
  }
}
