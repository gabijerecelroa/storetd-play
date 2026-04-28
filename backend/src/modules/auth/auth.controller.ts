import { Body, Controller, Post } from '@nestjs/common';

@Controller('auth')
export class AuthController {
  @Post('login')
  login(@Body() body: { username: string; password: string }) {
    return {
      accessToken: 'demo-token',
      refreshToken: 'demo-refresh-token',
      customer: {
        id: 'demo',
        username: body.username,
        status: 'TRIAL',
      },
    };
  }

  @Post('activate')
  activate(@Body() body: { code: string; deviceUid: string }) {
    return {
      accessToken: 'demo-token',
      activationCode: body.code,
      deviceLinked: true,
    };
  }

  @Post('refresh')
  refresh() {
    return { accessToken: 'demo-token' };
  }

  @Post('logout')
  logout() {
    return { success: true };
  }
}
