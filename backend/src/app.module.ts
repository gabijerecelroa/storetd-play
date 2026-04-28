import { Module } from '@nestjs/common';
import { PrismaService } from './common/prisma.service';
import { AuthController } from './modules/auth/auth.controller';
import { CustomersController } from './modules/customers/customers.controller';
import { PlaylistsController } from './modules/playlists/playlists.controller';
import { ReportsController } from './modules/reports/reports.controller';
import { AppConfigController } from './modules/app-config/app-config.controller';

@Module({
  controllers: [
    AuthController,
    CustomersController,
    PlaylistsController,
    ReportsController,
    AppConfigController,
  ],
  providers: [PrismaService],
})
export class AppModule {}
