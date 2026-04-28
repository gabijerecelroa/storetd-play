import { Body, Controller, Get, Put } from '@nestjs/common';

@Controller()
export class AppConfigController {
  @Get('app/config')
  getPublicConfig() {
    return {
      appName: 'StoreTD Play',
      primaryColor: '#E50914',
      secondaryColor: '#141414',
      supportWhatsapp: '5490000000000',
      supportEmail: 'soporte@example.com',
      maintenanceMode: false,
    };
  }

  @Get('admin/app-config')
  getAdminConfig() {
    return this.getPublicConfig();
  }

  @Put('admin/app-config')
  updateConfig(@Body() body: unknown) {
    return body;
  }
}
